package com.github.theguuholi.recommendations.rentals;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/** Real NameNode, DataNode and HttpFS, isolated in one test-only container. */
class HdfsTestContainer extends GenericContainer<HdfsTestContainer> {
  HdfsTestContainer() {
    super("apache/hadoop:3.5.0");
    withExposedPorts(14000);
    withCopyToContainer(
        MountableFile.forClasspathResource("hdfs-test-cluster.sh"), "/tmp/start.sh");
    withCreateContainerCmdModifier(command -> command.withEntrypoint("bash"));
    withCommand("/tmp/start.sh");
    waitingFor(
        Wait.forHttp("/webhdfs/v1/?op=LISTSTATUS&user.name=hadoop")
            .forPort(14000)
            .forStatusCode(200));
    withStartupTimeout(Duration.ofMinutes(2));
  }

  String endpoint() {
    // Docker Desktop may allocate a different host port after a container restart.
    var port =
        getDockerClient()
            .inspectContainerCmd(getContainerId())
            .exec()
            .getNetworkSettings()
            .getPorts()
            .getBindings()
            .get(new com.github.dockerjava.api.model.ExposedPort(14000))[0]
            .getHostPortSpec();
    return "http://" + getHost() + ":" + port;
  }
}
