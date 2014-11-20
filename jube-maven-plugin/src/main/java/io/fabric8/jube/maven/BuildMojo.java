/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.jube.maven;

import io.fabric8.jube.util.ImageMavenCoords;
import io.fabric8.jube.util.InstallHelper;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Zips;


import org.apache.commons.io.FileUtils;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugin.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugin.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugin.assembly.io.AssemblyReadException;
import org.apache.maven.plugin.assembly.io.AssemblyReader;
import org.apache.maven.plugin.assembly.model.Assembly;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.utils.PropertiesHelper.findPropertiesWithPrefix;

/**
 * Builds a Jube  image from a known base image.
 */
@Mojo(name = "build")
public class BuildMojo extends AbstractMojo {
    public static final String DOCKER_IMAGE_PROPERTY = "docker.image";
    public static final String DOCKER_BASE_IMAGE_PROPERTY = "docker.from";

    private static final transient Logger LOG = LoggerFactory.getLogger(BuildMojo.class);

    @Parameter(property = "docker.image", defaultValue = "${project.groupId}/${project.artifactId}")
    private String image;

    @Parameter(property = "docker.from", defaultValue = "fabric8/java")
    private String baseImage;

    @Parameter(property = "exportDir", defaultValue = "/maven")
    private String exportDir;

    @Parameter
    private Map<String, String> environmentVariables;

    @Parameter
    private Map<String, String> ports;

    /**
     * A descriptor to use for building the data assembly to be exported in an Docker image
     */
    @Parameter(property = "docker.assemblyDescriptor")
    private String assemblyDescriptor;

    /**
     * Reference to an assembly descriptor included.
     */
    @Parameter(property = "docker.assemblyDescriptorRef", defaultValue = "artifact-with-dependencies")
    private String assemblyDescriptorRef;

    /**
     * Name of the generated image zip file
     */
    @Parameter(property = "jube.outFile", defaultValue = "${project.build.directory}/${project.artifactId}-${project.version}-image.zip")
    private File outputZipFile;

    /**
     * The artifact type for attaching the generated image zip file to the project
     */
    @Parameter(property = "jube.zip.artifactType", defaultValue = "zip")
    private String artifactType = "zip";

    /**
     * The artifact classifier for attaching the generated image zip file to the project
     */
    @Parameter(property = "jube.zip.artifactClassifier", defaultValue = "image")
    private String artifactClassifier = "image";

    /**
     * Generates the SERVICE environment variable
     */
    @Parameter(property = "fabric8.service", defaultValue = "${project.artifactId}")
    private String service;

    /**
     * Generates the SERVICE_NAME environment variable
     */
    @Parameter(property = "fabric8.serviceName", defaultValue = "${project.name}")
    private String serviceName;

    // ==============================================================================================================
    // Parameters required from Maven when building an assembly.
    @Parameter
    private MavenArchiveConfiguration archive;

    @Component
    private MavenProject project;

    @Component
    private MavenSession session;

    @Component
    private MavenFileFilter mavenFileFilter;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactRepositoryFactory artifactRepositoryFactory;

    @Parameter(defaultValue = "${localRepository}", readonly = true)
    private ArtifactRepository localRepository;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> pomRemoteRepositories;

    @Component
    private AssemblyArchiver assemblyArchiver;

    @Component
    private AssemblyReader assemblyReader;

    @Component
    private ArchiverManager archiverManager;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isIgnoreProject()) {
            return;
        }
        getLog().info("Generating Jube image " + image + " from base image " + baseImage);

        getLog().info("Assembly reference: " + assemblyDescriptorRef);

        if (assemblyDescriptor == null && assemblyDescriptorRef == null) {
            throw new MojoExecutionException("No assemblyDescriptor or assemblyDescriptorRef has been given");
        }

        System.out.println("Env vars: " + getEnvironmentVariables());

        createAssembly();
    }

    /**
     * Returns true if this project should be ignored
     */
    protected boolean isIgnoreProject() {
        return "pom".equals(project.getPackaging());
    }

    public Map<String, String> getEnvironmentVariables() {
        if (environmentVariables == null) {
            environmentVariables = new HashMap<>();
        }
        if (environmentVariables.isEmpty()) {
            environmentVariables = findPropertiesWithPrefix(project.getProperties(), "docker.env.");
        }
        if (!environmentVariables.containsKey("SERVICE")) {
            environmentVariables.put("SERVICE", service);
        }
        if (!environmentVariables.containsKey("SERVICE_NAME")) {
            environmentVariables.put("SERVICE_NAME", serviceName);
        }
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public Map<String, String> getPorts() {
        if (ports == null) {
            ports = new HashMap<>();
        }
        if (ports.isEmpty()) {
            ports = findPropertiesWithPrefix(project.getProperties(), "docker.port.container.");
        }
        return ports;
    }

    protected void createAssembly() throws MojoFailureException, MojoExecutionException {
        String extractBaseImageRef = "extractBaseImage";
        String createZipRef = "createZip";
        //String extractBaseImageRef = "internal/extractBaseImage.xml";
        AssemblerConfigurationSource extractConfig = createAssemblyConfigurationSource(null, extractBaseImageRef);
        AssemblerConfigurationSource projectConfig = createAssemblyConfigurationSource(assemblyDescriptor, assemblyDescriptorRef);
        AssemblerConfigurationSource zipConfig = createAssemblyConfigurationSource(null, createZipRef);

        Assembly assembly = null;

        File buildDir = new File(project.getBasedir(), "target/jube");
        try {
            buildDir.mkdirs();
            try {
                unpackBaseImage(buildDir, false);
            } catch (ArtifactResolutionException e) {
                unpackBaseImage(buildDir, true);
            } catch (ArtifactNotFoundException e) {
                unpackBaseImage(buildDir, true);
            }

            writeEnvironmentVariables(buildDir);
            writePorts(buildDir);

            assembly = extractAssembly(projectConfig);

            assembly.setId("docker");

            if (exportDir.startsWith("/")) {
                exportDir = exportDir.substring(1);
            }

            File assemblyDir = assemblyArchiver.createArchive(assembly, exportDir, "dir", projectConfig, false);
            FileUtils.copyDirectory(assemblyDir, buildDir);
            
            InstallHelper.chmodAllScripts(buildDir);

            Zips.createZipFile(LOG, buildDir, outputZipFile);
            getLog().info("Created image zip: " + outputZipFile);

            attachArtifactToBuild();

        } catch (InvalidAssemblerConfigurationException e) {
            throw new MojoFailureException(assembly, "Assembly is incorrectly configured: " + assembly.getId(),
                    "Assembly: " + assembly.getId() + " is not configured correctly: "
                            + e.getMessage());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create assembly for image: " + e.getMessage(), e);
        }
    }

    protected void attachArtifactToBuild() {
        projectHelper.attachArtifact(project, artifactType, artifactClassifier, outputZipFile);
    }

    protected void writeEnvironmentVariables(File buildDir) throws IOException {
        Map<String, String> envMap = getEnvironmentVariables();
        File envScript = new File(buildDir, InstallHelper.ENVIRONMENT_VARIABLE_SCRIPT);
        InstallHelper.writeEnvironmentVariables(envScript, envMap);
    }

    protected void writePorts(File buildDir) throws IOException {
        Map<String, String> portMap = getPorts();
        if (portMap.isEmpty()) {
            return;
        }
        File envScript = new File(buildDir, InstallHelper.PORTS_PROPERTIES_FILE);
        InstallHelper.writePorts(envScript, portMap);
    }

    protected void unpackBaseImage(File buildDir, boolean useDefaultPrefix) throws Exception {
        String imageName = project.getProperties().getProperty(DOCKER_BASE_IMAGE_PROPERTY);
        Objects.notNull(imageName, DOCKER_BASE_IMAGE_PROPERTY);

        ImageMavenCoords baseImageCoords = ImageMavenCoords.parse(imageName, useDefaultPrefix);
        String coords = baseImageCoords.getAetherCoords();
        getLog().info("Looking up Jube: " + coords);
        Artifact artifact = artifactFactory.createArtifactWithClassifier(baseImageCoords.getGroupId(),
                baseImageCoords.getArtifactId(), baseImageCoords.getVersion(), baseImageCoords.getType(),
                baseImageCoords.getClassifier());

        artifactResolver.resolve(artifact, pomRemoteRepositories, localRepository);

        System.out.println(artifact.getFile());

        if (artifact.getFile() != null) {
            File file = artifact.getFile();
            getLog().info("File: " + file);

            if (!file.exists() || file.isDirectory()) {
                throw new MojoExecutionException("Resolved file for " + coords + " is not a valid file: " + file.getAbsolutePath());
            }
            getLog().info("Unpacking base image " + file.getAbsolutePath() + " to build dir: " + buildDir);
            Zips.unzip(new FileInputStream(file), buildDir);
        }
    }

    private AssemblerConfigurationSource createAssemblyConfigurationSource(String descriptor, String descriptorRef) {
        String[] descriptors = null;
        String[] descriptorRefs = null;
        if (descriptor != null) {
            descriptors = new String[]{descriptor};
        } else if (descriptorRef != null) {
            descriptorRefs = new String[]{descriptorRef};
        }
        return new JubeArchiveConfigurationSource(project, session, archive, mavenFileFilter, descriptors, descriptorRefs);
    }

    private Assembly extractAssembly(AssemblerConfigurationSource config) throws MojoExecutionException, MojoFailureException {
        try {
            List<Assembly> assemblies = assemblyReader.readAssemblies(config);
            if (assemblies.size() != 1) {
                throw new MojoFailureException("Only one assembly can be used for creating a Docker base image (and not " + assemblies.size() + ")");
            }
            return assemblies.get(0);
        } catch (AssemblyReadException e) {
            throw new MojoExecutionException("Error reading assembly: " + e.getMessage(), e);
        } catch (InvalidAssemblerConfigurationException e) {
            throw new MojoFailureException(assemblyReader, e.getMessage(), "Docker assembly configuration is invalid: " + e.getMessage());
        }
    }

}
