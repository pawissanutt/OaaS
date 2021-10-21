package org.hpcclab.msc.provisioner;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import org.hpcclab.msc.object.entity.task.Task;

public class TaskDeserializer extends ObjectMapperDeserializer<Task> {
  public TaskDeserializer() {
    super(Task.class);
  }
}