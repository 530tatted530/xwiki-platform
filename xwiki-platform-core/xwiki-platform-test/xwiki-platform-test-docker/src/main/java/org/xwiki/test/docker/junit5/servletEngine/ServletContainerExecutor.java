/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.test.docker.junit5.servletEngine;

import java.io.File;
import java.io.FileWriter;
import java.time.Duration;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.xwiki.test.docker.junit5.AbstractContainerExecutor;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.integration.maven.ArtifactResolver;
import org.xwiki.test.integration.maven.MavenResolver;
import org.xwiki.test.integration.maven.RepositoryResolver;

import com.github.dockerjava.api.model.Image;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Create and execute the Docker Servlet engine container for the tests.
 *
 * @version $Id$
 * @since 10.9
 */
public class ServletContainerExecutor extends AbstractContainerExecutor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServletContainerExecutor.class);

    private static final String LATEST = "latest";

    private static final String CLOVER_DATABASE = System.getProperty("maven.clover.cloverDatabase");

    private JettyStandaloneExecutor jettyStandaloneExecutor;

    private RepositoryResolver repositoryResolver;

    private TestConfiguration testConfiguration;

    private GenericContainer servletContainer;

    /**
     * @param testConfiguration the configuration to build (database, debug mode, etc)
     * @param artifactResolver the resolver to resolve artifacts from Maven repositories
     * @param mavenResolver the resolver to read Maven POMs
     * @param repositoryResolver the resolver to create Maven repositories and sessions
     */
    public ServletContainerExecutor(TestConfiguration testConfiguration, ArtifactResolver artifactResolver,
        MavenResolver mavenResolver, RepositoryResolver repositoryResolver)
    {
        this.testConfiguration = testConfiguration;
        this.jettyStandaloneExecutor = new JettyStandaloneExecutor(testConfiguration, artifactResolver, mavenResolver);
        this.repositoryResolver = repositoryResolver;
    }

    /**
     * @param sourceWARDirectory the location where the built WAR is located
     * @throws Exception if an error occurred during the build or start
     */
    public void start(File sourceWARDirectory) throws Exception
    {
        String xwikiIPAddress = "localhost";
        int xwikiPort = 8080;

        switch (this.testConfiguration.getServletEngine()) {
            case TOMCAT:
                // Configure Tomcat logging for debugging. Create a logging.properties file
                File logFile = new File(sourceWARDirectory, "WEB-INF/classes/logging.properties");
                logFile.createNewFile();
                try (FileWriter writer = new FileWriter(logFile)) {
                    IOUtils.write("org.apache.catalina.core.ContainerBase.[Catalina].level = FINE\n"
                        + "org.apache.catalina.core.ContainerBase.[Catalina].handlers = "
                        + "java.util.logging.ConsoleHandler\n", writer);
                }
                this.servletContainer = createServletContainer();
                mountFromHostToContainer(this.servletContainer, sourceWARDirectory.toString(),
                    "/usr/local/tomcat/webapps/xwiki");

                this.servletContainer.withEnv("CATALINA_OPTS", "-Xmx1024m "
                    + "-Dorg.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH=true "
                    + "-Dorg.apache.catalina.connector.CoyoteAdapter.ALLOW_BACKSLASH=true "
                    + "-Dsecurerandom.source=file:/dev/urandom");

                break;
            case JETTY:
                this.servletContainer = createServletContainer();
                mountFromHostToContainer(this.servletContainer, sourceWARDirectory.toString(),
                    "/var/lib/jetty/webapps/xwiki");

                break;
            case WILDFLY:
                this.servletContainer = createServletContainer();
                mountFromHostToContainer(this.servletContainer, sourceWARDirectory.toString(),
                    "/opt/jboss/wildfly/standalone/deployments/xwiki");

                break;
            case JETTY_STANDALONE:
                // Resolve and unzip the xwiki-platform-tool-jetty-resources zip artifact and configure Jetty to
                // use the custom WAR that we generated. Then start jetty from the command line shell script.
                // Note that we could have decided to embed Jetty (see
                // http://www.eclipse.org/jetty/documentation/9.4.x/embedding-jetty.html) but we decided against that
                // as it would mean maintaining 2 Jetty configurations (one for the Jetty standalone packaging and
                // one for Jetty in embedded mode).
                this.jettyStandaloneExecutor.start();
                break;
            default:
                throw new RuntimeException(String.format("Servlet engine [%s] is not yet supported!",
                    testConfiguration.getServletEngine()));
        }

        if (this.servletContainer != null) {

            startContainer();

            xwikiIPAddress = this.servletContainer.getContainerIpAddress();
            xwikiPort =
                this.servletContainer.getMappedPort(this.testConfiguration.getServletEngine().getInternalPort());
        }

        this.testConfiguration.getServletEngine().setIP(xwikiIPAddress);
        this.testConfiguration.getServletEngine().setPort(xwikiPort);
    }

    private void startContainer()
    {
        // Note: TestContainers will wait for up to 60 seconds for the container's first mapped network port to
        // start listening.
        this.servletContainer
            .withNetwork(Network.SHARED)
            .withNetworkAliases(this.testConfiguration.getServletEngine().getInternalIP())
            .withExposedPorts(this.testConfiguration.getServletEngine().getInternalPort())
            .waitingFor(
                Wait.forHttp("/xwiki/bin/get/Main/WebHome")
                    .forStatusCode(200).withStartupTimeout(Duration.of(480, SECONDS)));

        if (this.testConfiguration.isOffline()) {
            // Note: This won't work in the DOOD use case. For that to work we would need to copy the data instead
            // of mounting the volume but the time it would take would be too costly.
            String repoLocation = this.repositoryResolver.getSession().getLocalRepository().getBasedir().toString();
            this.servletContainer.withFileSystemBind(repoLocation, "/root/.m2/repository");
        }

        // If the Clover database system property is setup, then copy or map the clover database location on the FS to
        // a path inside the container
        if (CLOVER_DATABASE != null && !this.testConfiguration.getServletEngine().isOutsideDocker()) {
            // Note 1: The Clover instrumentation puts the full path to the clover database location inside the java
            // source files which execute inside the docker container. Thus we need to make that exact same full path
            // available inside the container...
            // Note 2: For this to work in DOOD (Docker Outside Of Docker), the container in which this code is
            // running will need to have mapped the volume pointed to by the "maven.clover.cloverDatabase" system
            // property. It'll also need to make sure that it's removed before the build executes so that it doesn't
            // execute with Clover data from a previous run.
            // Note 3: The copy is done the other way around when the container is stopped so that the new added
            // Clover data is available from the Maven container for computing the Clover report.
            mountFromHostToContainer(this.servletContainer, CLOVER_DATABASE, CLOVER_DATABASE);
        }

        start(this.servletContainer, this.testConfiguration);
    }

    private String getDockerImageTag(TestConfiguration testConfiguration)
    {
        return testConfiguration.getServletEngineTag() != null ? testConfiguration.getServletEngineTag() : LATEST;
    }

    private GenericContainer createServletContainer()
    {
        final String baseImageName = String.format("%s:%s",
            this.testConfiguration.getServletEngine().getDockerImageName(), getDockerImageTag(this.testConfiguration));
        final GenericContainer container;

        if (this.testConfiguration.isOffice()) {
            // We only build the image once for performance reason.
            // So we provide a name to the image we will built and we check that the image does not exist yet.
            String imageName = String.format("xwiki-%s-office:%s",
                this.testConfiguration.getServletEngine().name().toLowerCase(), getDockerImageTag(testConfiguration));

            List<Image> imageSearchResults = DockerClientFactory.instance().client().listImagesCmd()
                .withImageNameFilter(imageName).exec();

            if (imageSearchResults.isEmpty()) {
                LOGGER.info("(*) Build a dedicated image embedding LibreOffice...");
                // The second argument of the ImageFromDockerfile is here to indicate we won't delete the image
                // at the end of the test container execution.
                container = new GenericContainer(new ImageFromDockerfile(imageName, false)
                    .withDockerfileFromBuilder(builder ->
                        builder
                            .from(baseImageName)
                            .user("root")
                            // This command is inspired from XWiki dockerfiles
                            // see for example:
                            // https://github.com/xwiki-contrib/docker-xwiki/blob/master/10/mysql-tomcat/Dockerfile
                            .run("apt-get update && apt-get install -y"
                                + " curl"
                                + " libreoffice"
                                + " unzip"
                                + " procps")
                            .build()));
            } else {
                container = new GenericContainer(imageName);
            }
        } else {
            container = new GenericContainer(baseImageName);
        }

        return container;
    }

    /**
     * @throws Exception if an error occurred during the stop
     */
    public void stop() throws Exception
    {
        switch (this.testConfiguration.getServletEngine()) {
            case JETTY_STANDALONE:
                this.jettyStandaloneExecutor.stop();
                break;
            default:
                // Nothing else to do, TestContainers automatically stops the container
        }

        // If the Clover database system property is setup, then copy back the data to the parent container if need be.
        if (CLOVER_DATABASE != null && !this.testConfiguration.getServletEngine().isOutsideDocker()
            && isInAContainer())
        {
            this.servletContainer.copyFileFromContainer(CLOVER_DATABASE, CLOVER_DATABASE);
        }
    }
}
