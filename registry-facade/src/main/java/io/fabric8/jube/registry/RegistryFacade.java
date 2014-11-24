package io.fabric8.jube.registry;

import java.io.File;
import java.io.IOException;
import java.util.*;

import io.hawt.maven.indexer.ArtifactDTO;
import io.hawt.maven.indexer.MavenIndexerFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;

/**
 * Simple fake docker registry that searches maven for app zips
 */
@Singleton
public class RegistryFacade {

    private static final transient Logger LOG = LoggerFactory.getLogger(RegistryFacade.class);
    public static final String SEPARATOR = ":";

    MavenIndexerFacade mavenIndexerFacade = new MavenIndexerFacade();

    public RegistryFacade() {
    }

    public File getMavenIndexerCacheDirectory() {
        return mavenIndexerFacade.getCacheDirectory();
    }

    @PostConstruct
    public void init() {
        try {
            mavenIndexerFacade.init();
        } catch (Exception e) {
            LOG.warn("Error initializing MavenIndexer due " + e.getMessage() + ". This exception is ignored.", e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            mavenIndexerFacade.destroy();
        } catch (Exception e) {
            LOG.debug("Error destroying MavenIndexer due " + e.getMessage() + ". This exception is ignored.", e);
        }
    }

    private String toMavenCoordinates(ArtifactDTO artifact) {
        return artifact.getGroupId() + SEPARATOR + artifact.getArtifactId() + SEPARATOR + artifact.getVersion() + SEPARATOR + artifact.getPackaging() + SEPARATOR + artifact.getClassifier();
    }

    private ArtifactDTO fromMavenCoordinates(String coordinates) {
        List<String> parts = Arrays.asList(coordinates.split(SEPARATOR));
        String groupId = null, artifactId = null, version = null, classifier = null, packaging = null;
        try {
            groupId = parts.get(0);
            artifactId = parts.get(1);
            version = parts.get(2);
            packaging = parts.get(3);
            classifier = parts.get(4);
        } catch (IndexOutOfBoundsException e) {
            // silently ignore;
        }
        return new ArtifactDTO(groupId, artifactId, version, packaging, classifier, null, 0, null, null);
    }

    private List<ArtifactDTO> findSimilarArtifacts(List<ArtifactDTO> results, ArtifactDTO search) {
        List<ArtifactDTO> answer = new ArrayList<ArtifactDTO>();
        for (ArtifactDTO item : results) {
            if (item.getGroupId().equals(search.getGroupId()) && item.getArtifactId().equals(search.getArtifactId())) {
                answer.add(item);
            }
        }
        return answer;
    }

    public RepositoriesDTO search(String query) {
        if (query == null) {
            query = "";
        }
        RepositoriesDTO answer = new RepositoriesDTO();
        answer.query = query;
        answer.num_results = 0;
        answer.results = new ArrayList<RepositoryDTO>();
        try {
            List<ArtifactDTO> results = mavenIndexerFacade.searchTextAndPackaging(query, null, "image");
            while (!results.isEmpty()) {
                ArtifactDTO result = results.remove(0);
                if (result.getPackaging().equals("zip")) {
                    RepositoryDTO repository = new RepositoryDTO();
                    repository.name = result.getGroupId() + "/" + result.getArtifactId();
                    repository.description = result.getDescription();
                    List<ArtifactDTO> similar = findSimilarArtifacts(results, result);
                    results.removeAll(similar);
                    answer.results.add(repository);
                }
            }
        } catch (IOException e) {
            // ignore
            LOG.debug("Failed to fetch results from indexer facade", e);
        }
        return answer;
    }

    public Map<String, String> tags(String namespace, String repository) {
        Map<String, String> answer = new HashMap<String, String>();
        try {
            List<ArtifactDTO> results = mavenIndexerFacade.search(namespace, repository, null, null, "image", null);
            for (ArtifactDTO result : results) {
                if (result.getPackaging().equals("zip")) {
                    answer.put(result.getVersion(), toMavenCoordinates(result));
                }
            }
        } catch (IOException e) {
            // ignore
            LOG.debug("Failed to fetch results from indexer facade", e);
        }
        return answer;
    }

    public Object imageJson(String imageID) {
        ArtifactDTO artifact = fromMavenCoordinates(imageID);
        try {
            List<ArtifactDTO> results = mavenIndexerFacade.search(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getPackaging(), artifact.getClassifier(), null);
            if (!results.isEmpty()) {
                return results.get(0);
            }
        } catch (IOException e) {
            // ignore
            LOG.debug("Failed to fetch results from indexer facade", e);
        }
        LOG.debug("No results found for image ID: {}", imageID);
        return null;
    }

}
