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

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.assembly.AssemblerConfigurationSource;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;

public class JubeArchiveConfigurationSource implements AssemblerConfigurationSource {
    private final MavenProject project;
    private final MavenSession mavenSession;
    private final MavenArchiveConfiguration archiveConfiguration;
    private final MavenFileFilter mavenFilter;
    private String[] descriptors;
    private String[] descriptorRefs;

    public JubeArchiveConfigurationSource(MavenProject project, MavenSession mavenSession, MavenArchiveConfiguration archiveConfiguration,
                                          MavenFileFilter mavenFilter, String[] descriptors, String[] descriptorRefs) {
        this.project = project;
        this.mavenSession = mavenSession;
        this.archiveConfiguration = archiveConfiguration;
        this.mavenFilter = mavenFilter;
        this.descriptors = descriptors;
        this.descriptorRefs = descriptorRefs;
    }

    public String[] getDescriptors() {
        return descriptors;
    }

    public String[] getDescriptorReferences() {
        return descriptorRefs;
    }

    // ============================================================================================

    public File getOutputDirectory() {
        return new File(project.getBasedir(), "target/jube-out");
    }

    public File getWorkingDirectory() {
        return new File(project.getBasedir(), "target/jube-work");
    }

    public File getTemporaryRootDirectory() {
        return new File(project.getBasedir(), "target/jube-tmp");
    }

    public String getFinalName() {
        return project.getBuild().getFinalName();
    }

    public ArtifactRepository getLocalRepository() {
        return getMavenSession().getLocalRepository();
    }

    public MavenFileFilter getMavenFileFilter() {
        return mavenFilter;
    }

    // Maybe use injection
    public List<MavenProject> getReactorProjects() {
        return project.getCollectedProjects();
    }

    // Maybe use injection
    public List<ArtifactRepository> getRemoteRepositories() {
        return project.getRemoteArtifactRepositories();
    }

    public MavenSession getMavenSession() {
        return mavenSession;
    }

    public MavenArchiveConfiguration getJarArchiveConfiguration() {
        return archiveConfiguration;
    }

    // X
    public String getEncoding() {
        return project.getProperties().getProperty("project.build.sourceEncoding");
    }

    // X
    public String getEscapeString() {
        return null;
    }

    // X
    public MavenProject getProject() {
        return project;
    }

    // X
    public File getBasedir() {
        return project.getBasedir();
    }

    // X
    public boolean isIgnoreDirFormatExtensions() {
        return true;
    }

    // X
    public boolean isDryRun() {
        return false;
    }

    // X
    public String getClassifier() {
        return null;
    }

    // X
    public List<String> getFilters() {
        return Collections.emptyList();
    }

    // X
    public File getDescriptorSourceDirectory() {
        return null;
    }

    // X
    public File getArchiveBaseDirectory() {
        return null;
    }

    // X
    public String getDescriptorId() {
        return null;
    }

    // X
    public String getDescriptor() {
        return null;
    }

    // X
    public String getTarLongFileMode() {
        return "warn";
    }

    // X
    public File getSiteDirectory() {
        return null;
    }

    // X
    public boolean isSiteIncluded() {
        return false;
    }

    // X
    public boolean isAssemblyIdAppended() {
        return false;
    }

    // X
    public boolean isIgnoreMissingDescriptor() {
        return false;
    }

    // X: (maybe inject MavenArchiveConfiguration)
    public String getArchiverConfig() {
        return null;
    }

    public boolean isUpdateOnly() {
        return false;
    }

    public boolean isUseJvmChmod() {
        return false;
    }

    public boolean isIgnorePermissions() {
        return true;
    }
}
