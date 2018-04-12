package com.google.devtools.build.remote.client;

import com.google.devtools.remoteexecution.v1test.Action;
import com.google.devtools.remoteexecution.v1test.Command;
import com.google.devtools.remoteexecution.v1test.Command.EnvironmentVariable;
import com.google.devtools.remoteexecution.v1test.Platform;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public final class DockerUtil {
  private static final String CONTAINER_IMAGE_ENTRY_NAME = "container-image";
  private static final String DOCKER_IMAGE_PREFIX = "docker://";

  /**
   * Checks Action for Docker container definition.
   *
   * @return The docker container for the Action. If not container could be found, returns null.
   */
  private static @Nullable String dockerContainer(Action action) {
    String result = null;
    for (Platform.Property property : action.getPlatform().getPropertiesList()) {
      if (property.getName().equals(CONTAINER_IMAGE_ENTRY_NAME)) {
        if (result != null) {
          // Multiple container name entries
          throw new IllegalArgumentException(
              String.format(
                  "Multiple entries for %s in action.Platform", CONTAINER_IMAGE_ENTRY_NAME));
        }
        result = property.getValue();
        if (!result.startsWith(DOCKER_IMAGE_PREFIX)) {
          throw new IllegalArgumentException(
              String.format(
                  "%s: Docker images must be stored in gcr.io with an image spec in the form "
                      + "'docker://gcr.io/{IMAGE_NAME}'",
                  CONTAINER_IMAGE_ENTRY_NAME));
        }
        result = result.substring(DOCKER_IMAGE_PREFIX.length());
      }
    }
    return result;
  }

  /**
   * Outputs a Docker command that will execute the given action in the given path.
   *
   * @param action The Action to be executed in the output docker container command.
   * @param command The Command of the Action being executed. This must match the Command that is
   *     referred to from the input parameter Action.
   * @param workingPath The path that is to be the working directory that the Action is to be
   *     executed in.
   */
  public static String getDockerCommand(Action action, Command command, String workingPath) {
    String container = dockerContainer(action);
    if (container == null) {
      throw new IllegalArgumentException("No docker image specified in given Action.");
    }
    List<String> commandElements = new ArrayList<>();
    commandElements.add("docker");
    commandElements.add("run");

    String dockerPathString = workingPath + "-docker";
    commandElements.add("-v");
    commandElements.add(workingPath + ":" + dockerPathString);
    commandElements.add("-w");
    commandElements.add(dockerPathString);

    for (EnvironmentVariable var : command.getEnvironmentVariablesList()) {
      commandElements.add("-e");
      commandElements.add(var.getName() + "=" + var.getValue());
    }

    commandElements.add(container);
    commandElements.addAll(command.getArgumentsList());

    return ShellEscaper.escapeJoinAll(commandElements);
  }
}
