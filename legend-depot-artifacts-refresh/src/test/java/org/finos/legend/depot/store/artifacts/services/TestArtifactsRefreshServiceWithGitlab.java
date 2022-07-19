//  Copyright 2021 Goldman Sachs
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

package org.finos.legend.depot.store.artifacts.services;

import org.finos.legend.depot.artifacts.repository.api.ArtifactRepository;
import org.finos.legend.depot.artifacts.repository.domain.ArtifactType;
import org.finos.legend.depot.artifacts.repository.maven.impl.MavenArtifactRepository;
import org.finos.legend.depot.artifacts.repository.maven.impl.MavenArtifactRepositoryConfiguration;
import org.finos.legend.depot.domain.api.MetadataEventResponse;
import org.finos.legend.depot.domain.api.status.MetadataEventStatus;
import org.finos.legend.depot.domain.project.IncludeProjectPropertiesConfiguration;
import org.finos.legend.depot.domain.project.ProjectData;
import org.finos.legend.depot.services.entities.EntitiesServiceImpl;
import org.finos.legend.depot.services.projects.ProjectsServiceImpl;
import org.finos.legend.depot.store.api.entities.UpdateEntities;
import org.finos.legend.depot.store.api.generation.file.UpdateFileGenerations;
import org.finos.legend.depot.store.api.projects.UpdateProjects;
import org.finos.legend.depot.store.artifacts.api.ArtifactsRefreshService;
import org.finos.legend.depot.store.artifacts.api.entities.EntityArtifactsProvider;
import org.finos.legend.depot.store.artifacts.api.generation.file.FileGenerationsProvider;
import org.finos.legend.depot.store.artifacts.api.status.ManageRefreshStatusService;
import org.finos.legend.depot.store.artifacts.services.entities.EntitiesHandlerImpl;
import org.finos.legend.depot.store.artifacts.services.entities.EntityProvider;
import org.finos.legend.depot.store.artifacts.services.file.FileGenerationsProviderImpl;
import org.finos.legend.depot.store.artifacts.store.mongo.ArtifactsMongo;
import org.finos.legend.depot.store.artifacts.store.mongo.MongoRefreshStatus;
import org.finos.legend.depot.store.artifacts.store.mongo.api.UpdateArtifacts;
import org.finos.legend.depot.store.mongo.TestStoreMongo;
import org.finos.legend.depot.store.mongo.entities.EntitiesMongo;
import org.finos.legend.depot.store.mongo.generation.file.FileGenerationsMongo;
import org.finos.legend.depot.store.mongo.projects.ProjectsMongo;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.finos.legend.depot.domain.version.VersionValidator.MASTER_SNAPSHOT;
import static org.junit.Assert.assertEquals;

public class TestArtifactsRefreshServiceWithGitlab extends TestStoreMongo
{
    public static final String TEST_GROUP_ID = "examples.metadata";
    public static final String TEST_ARTIFACT_ID = "test";
    public static final String TEST_DEPENDENCIES_ARTIFACT_ID = "test-dependencies";
    public static final String PROJECT_A = "PROD-23992";
    public static final String PROJECT_B = "PROD-23993";
    protected UpdateArtifacts artifacts = new ArtifactsMongo(mongoProvider);
    protected UpdateProjects projectsStore = new ProjectsMongo(mongoProvider);
    protected UpdateEntities entitiesStore = new EntitiesMongo(mongoProvider);
    protected List<String> properties = Arrays.asList("[a-zA-Z0-9]+.version");
    protected UpdateFileGenerations fileGenerationsStore = new FileGenerationsMongo(mongoProvider);
    protected ManageRefreshStatusService refreshStatusStore = new MongoRefreshStatus(mongoProvider);

    protected EntityArtifactsProvider entitiesProvider = new EntityProvider();
    protected FileGenerationsProvider fileGenerationsProvider = new FileGenerationsProviderImpl();

    protected ArtifactRepository repository;
    protected ArtifactsRefreshService artifactsRefreshService;

    @Before
    public void setup()
    {
        ArtifactResolverFactory.registerVersionUpdater(ArtifactType.ENTITIES, new EntitiesHandlerImpl(new EntitiesServiceImpl(entitiesStore, projectsStore), entitiesProvider));
    }

    // https://gitlab.com/finosfoundation/legend/showcase/legend-showcase-project2/
    // https://gitlab.com/finosfoundation/legend/showcase/legend-showcase-project2/-/packages/6324214
    @Test
    public void testProject1() throws Exception
    {
        Path settingsXMLFilePath = loadSettingsXML("/settings1.xml");
        System.out.println("Using settings xml from " + settingsXMLFilePath.toAbsolutePath());

        initRepository(settingsXMLFilePath);

        String groupId = "org.finos.legend.showcase";
        String artifactId = "showcase2";
        projectsStore.createOrUpdate(new ProjectData("unused", groupId, artifactId));

        MetadataEventResponse response = artifactsRefreshService.refreshProjectVersionArtifacts(groupId, artifactId, MASTER_SNAPSHOT, false);
        Assert.assertNotNull(response);
        assertEquals(MetadataEventStatus.SUCCESS, response.getStatus());

        List<Entity> entities = entitiesStore.getEntities(groupId, artifactId, MASTER_SNAPSHOT, false);
        String expected = "connection::bq;connection::h2;database::bq;database::h2;domain::Firm;domain::Firm2;domain::Person;mapping::firmpersonBQ;mapping::firmpersonH2;runtime::firmpersonH2;service::firmPersons;service::persons";
        String actual = entities.stream().map(e -> e.getPath()).sorted().collect(Collectors.joining(";"));
        assertEquals(expected, actual);
    }

    // https://gitlab.com/p948/LegendPipelinePOC
    // https://gitlab.com/p948/LegendPipelinePOC/-/packages/3081262
    @Test
    public void testProject2() throws Exception
    {
        Path settingsXMLFilePath = loadSettingsXML("/settings2.xml");
        System.out.println("Using settings xml from " + settingsXMLFilePath.toAbsolutePath());

        initRepository(settingsXMLFilePath);

        String groupId = "org.finos.legend";
        String artifactId = "test-project";
        projectsStore.createOrUpdate(new ProjectData("unused", groupId, artifactId));

        MetadataEventResponse response = artifactsRefreshService.refreshProjectVersionArtifacts(groupId, artifactId, MASTER_SNAPSHOT, false);
        Assert.assertNotNull(response);
        assertEquals(MetadataEventStatus.SUCCESS, response.getStatus());

        List<Entity> entities = entitiesStore.getEntities(groupId, artifactId, MASTER_SNAPSHOT, false);
        String expected = "foo,bar,baz";
        String actual = entities.stream().map(e -> e.getPath()).sorted().collect(Collectors.joining(";"));
        assertEquals(expected, actual);
    }

    private void initRepository(Path settingsXMLFilePath)
    {
        MavenArtifactRepositoryConfiguration mavenArtifactRepositoryConfiguration = new MavenArtifactRepositoryConfiguration(settingsXMLFilePath.toAbsolutePath().toString());
        this.repository = new MavenArtifactRepository(mavenArtifactRepositoryConfiguration);
        this.artifactsRefreshService = new ArtifactsRefreshServiceImpl(new ProjectsServiceImpl(projectsStore), refreshStatusStore, repository, artifacts, new IncludeProjectPropertiesConfiguration(properties));
    }

    private Path loadSettingsXML(String resourceName) throws IOException, URISyntaxException
    {
        byte[] settingsXMLBytes = Files.readAllBytes(Paths.get(TestArtifactsRefreshServiceWithGitlab.class.getResource(resourceName).toURI()));
        Path settingsXMLFilePath = Files.createTempDirectory("temp").resolve("settings.xml");
        Files.write(settingsXMLFilePath, settingsXMLBytes);
        return settingsXMLFilePath;
    }
}
