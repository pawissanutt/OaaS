package org.hpcclab.oaas.taskgen.stream;

import io.vertx.core.json.Json;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.hpcclab.oaas.model.TaskEvent;
import org.hpcclab.oaas.model.TaskState;
import org.hpcclab.oaas.taskgen.TaskEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;

public class TaskEventTransformer implements Transformer<String, TaskEvent, Iterable<KeyValue<String, TaskEvent>>> {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskEventTransformer.class);
  final String storeName;
  final TaskEventManager taskEventManager;
  KeyValueStore<String, TaskState> tsStore;
  ProcessorContext context;


  public TaskEventTransformer(String storeName,
                              TaskEventManager taskEventManager) {
    this.storeName = storeName;
    this.taskEventManager = taskEventManager;
  }

  @Override
  public void init(ProcessorContext context) {
    this.context = context;
    tsStore = context.getStateStore(storeName);
  }

  @Override
  public List<KeyValue<String, TaskEvent>> transform(String key,
                                                     TaskEvent taskEvent) {
    return switch (taskEvent.getType()) {
      case CREATE -> handleCreate(key, taskEvent);
      case NOTIFY -> handleNotify(key, taskEvent);
      case COMPLETE -> handleComplete(key, taskEvent);
    };
  }

  @Override
  public void close() {
  }

  private List<KeyValue<String, TaskEvent>> handleCreate(String key,
                                                         TaskEvent taskEvent) {
    var taskState = tsStore.get(key);
    if (taskState==null) taskState = new TaskState();
    if (taskState.getNextTasks()==null) {
      taskState.setNextTasks(taskEvent.getNextTasks());
    } else {
      taskState.getNextTasks().addAll(taskEvent.getNextTasks());
    }

    if (taskEvent.getPrevTasks()!=null && taskState.getPrevTasks()==null) {
      taskState.setPrevTasks(taskEvent.getPrevTasks());
    }

    if (taskState.getCompletedPrevTasks() == null) {
      taskState.setCompletedPrevTasks(new HashSet<>());
    }
    taskState.getCompletedPrevTasks().addAll(taskEvent.getRoots());

    List<KeyValue<String, TaskEvent>> kvList = List.of();

    if (taskState.getPrevTasks()==null || taskState.getPrevTasks().isEmpty()) {
      kvList = notifyNext(key, taskEvent.isExec(), taskState);
    } else if (taskState.isComplete()) {
      kvList = notifyNext(key, taskEvent.isExec(), taskState);
    } else if (taskEvent.getTraverse() > 0) {
      var eventList = taskEventManager.createEventWithTraversal(
          key,
          taskEvent.getTraverse(),
          taskEvent.isExec(),
          taskEvent.getType()
        );

      if (!eventList.isEmpty()) {
        taskState.getCompletedPrevTasks()
          .addAll(eventList.get(0).getRoots());
      }

      kvList = eventList.stream()
        .skip(1)
        .map(te -> KeyValue.pair(te.getId(), te))
        .toList();
    }

    if (taskEvent.isExec()) {
      if (taskState.getPrevTasks().equals(taskState.getCompletedPrevTasks())) {
        taskEventManager.submitTask(key);
      }
    }

    tsStore.put(key, taskState);
    LOGGER.debug("taskState {}", Json.encodePrettily(taskState));
    LOGGER.debug("Send new event {}", Json.encodePrettily(kvList));
    return kvList;
  }

  private List<KeyValue<String, TaskEvent>> handleNotify(String key,
                                                         TaskEvent taskEvent) {
    var taskState = tsStore.get(key);
    if (taskState.getCompletedPrevTasks()==null)
      taskState.setCompletedPrevTasks(new HashSet<>());
    taskState.getCompletedPrevTasks().add(taskEvent.getNotifyFrom());

    List<KeyValue<String, TaskEvent>> kvList = List.of();

    if (taskState.isComplete()) {
      kvList = notifyNext(key, taskEvent.isExec(), taskState);
    } else if (taskState.getPrevTasks().equals(taskState.getCompletedPrevTasks())) {
      taskEventManager.submitTask(key);
      taskState.setSubmitted(true);
    }

    tsStore.put(key, taskState);
    return kvList;
  }

  private List<KeyValue<String, TaskEvent>> handleComplete(String key,
                                                           TaskEvent taskEvent) {
    var taskState = tsStore.get(key);
    taskState.setComplete(true);
    tsStore.put(key, taskState);
    return notifyNext(key, taskEvent.isExec(), taskState);
  }

  private List<KeyValue<String, TaskEvent>> notifyNext(String key,
                                                       boolean exec,
                                                       TaskState taskState) {
    return taskState.getNextTasks()
      .stream()
      .map(id -> new TaskEvent()
        .setId(id)
        .setType(TaskEvent.Type.NOTIFY)
        .setExec(exec)
        .setNotifyFrom(key))
      .map(te -> KeyValue.pair(te.getId(), te))
      .toList();
  }
}
