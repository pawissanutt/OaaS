package org.hpcclab.oaas.taskmanager.service;

import io.micrometer.core.annotation.Timed;
import io.vertx.core.json.Json;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hpcclab.oaas.model.task.TaskEvent;
import org.hpcclab.oaas.model.task.TaskState;
import org.hpcclab.oaas.repository.TaskStateRepository;
import org.hpcclab.oaas.taskmanager.TaskEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.util.*;

@ApplicationScoped
public class TaskEventProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskEventProcessor.class);

//  @Remote("TaskState")
//  RemoteCache<String, TaskState> remoteCache;
  @Inject
  TaskStateRepository taskStateRepo;
  @Inject
  TaskEventManager taskEventManager;
  @RestClient
  TaskBrokerService taskBrokerService;

  TransactionManager transactionManager;

  @PostConstruct
  void setup() {
//    this.transactionManager = lookup.getTransactionManager();
    this.transactionManager = taskStateRepo.getRemoteCache().getTransactionManager();
  }

  @Timed(value = "processEvents", percentiles={0.5,0.75,0.95,0.99})
  public void processEvents(List<TaskEvent> taskEvents) {
    for (TaskEvent taskEvent : taskEvents) {
      handle(taskEvent);
    }
  }

  public List<TaskEvent> handle(TaskEvent taskEvent) {
    try {
      var list = switch (taskEvent.getType()) {
        case CREATE -> handleCreate(taskEvent);
        case NOTIFY -> handleNotify(taskEvent);
        case COMPLETE -> handleComplete(taskEvent);
      };
      if (list.isEmpty()) {
        return list;
      } else {
        return list.stream()
          .flatMap(te -> handle(te).stream())
          .toList();
      }
    } catch (SystemException e) {
      throw new TaskEventException(e);
    }
  }

  private List<TaskEvent> handleCreate(TaskEvent taskEvent) throws SystemException {
//    UUID id = UUID.fromString(taskEvent.getId());
    List<TaskEvent> eventList = new ArrayList<>();
    var remoteCache = taskStateRepo.getRemoteCache();
//    try {
//      transactionManager.begin();

      var taskStateMetadata = remoteCache.getWithMetadata(taskEvent.getId());
      var version = taskStateMetadata == null? null: taskStateMetadata.getVersion();
      var taskState = taskStateMetadata == null? new TaskState(): taskStateMetadata.getValue();

      if (taskState.getNextTasks()==null) {
        taskState.setNextTasks(taskEvent.getNextTasks());
      } else {
        taskState.getNextTasks().addAll(taskEvent.getNextTasks());
      }

      if (taskEvent.getPrevTasks()!=null && taskState.getPrqTasks()==null) {
        taskState.setPrqTasks(taskEvent.getPrevTasks());
      }

      if (taskState.getCompletedPrqTasks()==null) {
        taskState.setCompletedPrqTasks(new HashSet<>());
      }
      taskState.getCompletedPrqTasks().addAll(taskEvent.getRoots());

      if (taskState.getPrqTasks()==null || taskState.getPrqTasks().isEmpty()) {
        eventList.addAll(notifyNext(taskEvent.getId(), taskEvent.isExec(), taskState));
      } else if (taskState.isComplete()) {
        eventList.addAll(notifyNext(taskEvent.getId(), taskEvent.isExec(), taskState));
      } else if (taskEvent.getTraverse() > 0) {
        var tmp = taskEventManager.createEventWithTraversal(
          taskEvent.getId(),
          taskEvent.getTraverse(),
          taskEvent.isExec(),
          taskEvent.getType()
        );
        eventList.addAll(tmp.stream().skip(1).toList());

        if (!eventList.isEmpty()) {
          taskState.getCompletedPrqTasks()
            .addAll(eventList.get(0).getRoots());
        }
      }

      if (taskEvent.isExec()) {
        if (taskState.getPrqTasks().equals(taskState.getCompletedPrqTasks()) && !taskState.isSubmitted()) {
          submitTask(taskEvent.getId());
          taskState.setSubmitted(true);
        }
      }


      taskStateRepo.put(taskEvent.getId(), taskState);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("taskState {}", Json.encodePrettily(taskState));
        LOGGER.debug("Send new event {}", Json.encodePrettily(eventList));
      }
//      transactionManager.commit();
//    } catch (Exception e) {
//      LOGGER.error("Catch exception on handleCreate", e);
//      transactionManager.rollback();
//    }
    return eventList;
  }

  private List<TaskEvent> notifyNext(String key,
                                     boolean exec,
                                     TaskState taskState) {
    if (taskState.getNextTasks()==null) {
      taskState.setNextTasks(Set.of());
      return List.of();
    }
    return taskState.getNextTasks()
      .stream()
      .map(id -> new TaskEvent()
        .setId(id)
        .setType(TaskEvent.Type.NOTIFY)
        .setExec(exec)
        .setNotifyFrom(key))
      .toList();
  }

  private List<TaskEvent> handleNotify(TaskEvent taskEvent) throws SystemException {
//    UUID id = UUID.fromString(taskEvent.getId());
    List<TaskEvent> eventList = new ArrayList<>();

//    try {
//      transactionManager.begin();
      var taskState = taskStateRepo.get(taskEvent.getId());
      if (taskState==null) taskState = new TaskState();
      if (taskState.getCompletedPrqTasks()==null)
        taskState.setCompletedPrqTasks(new HashSet<>());
      taskState.getCompletedPrqTasks().add(taskEvent.getNotifyFrom());
      if (taskState.isComplete()) {
        eventList.addAll(notifyNext(taskEvent.getId(), taskEvent.isExec(), taskState));
      } else if (taskState.getPrqTasks().equals(taskState.getCompletedPrqTasks())
        && !taskState.isSubmitted()) {
        submitTask(taskEvent.getId());
        taskState.setSubmitted(true);
      }
      taskStateRepo.put(taskEvent.getId(), taskState);

//      transactionManager.commit();
//    } catch (Exception e) {
//      LOGGER.error("Catch exception on handleNotify", e);
//      transactionManager.rollback();
//    }
    return eventList;
  }

  private List<TaskEvent> handleComplete(TaskEvent taskEvent) throws SystemException {
//    UUID id = UUID.fromString(taskEvent.getId());

    List<TaskEvent> eventList = new ArrayList<>();

//    try {
//      transactionManager.begin();
      var taskState = taskStateRepo.get(taskEvent.getId());
      if (taskState==null) taskState = new TaskState();
      taskState.setComplete(true);
      taskStateRepo.put(taskEvent.getId(), taskState);
      eventList.addAll(notifyNext(taskEvent.getId(), taskEvent.isExec(), taskState));
//      transactionManager.commit();
//    } catch (Exception e) {
//      LOGGER.error("Catch exception on handleComplete", e);
//      transactionManager.rollback();
//    }
    return eventList;
  }

  public void submitTask(String id) {
    var task = taskEventManager.createTask(id);
    taskBrokerService.submitTask(id,
      task.getFunction().getName(),
      task.getFunction().getProvision().getType().toString(),
      task
      );
  }
}
