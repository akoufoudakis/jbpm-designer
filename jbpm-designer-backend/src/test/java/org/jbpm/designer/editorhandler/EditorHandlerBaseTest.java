/**
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.designer.editorhandler;

import org.jbpm.designer.helper.TestHttpServletRequest;
import org.jbpm.designer.helper.TestServletConfig;
import org.jbpm.designer.helper.TestServletContext;
import org.jbpm.designer.repository.Repository;
import org.jbpm.designer.repository.RepositoryBaseTest;
import org.jbpm.designer.repository.vfs.VFSRepository;
import org.jbpm.designer.server.EditorHandler;
import org.jbpm.designer.web.preprocessing.IDiagramPreprocessingService;
import org.jbpm.designer.web.profile.IDiagramProfileService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;


import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static junit.framework.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;


import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class EditorHandlerBaseTest extends RepositoryBaseTest {

    @Mock
    IDiagramProfileService profileService;

    @Mock
    IDiagramPreprocessingService preprocessingService;

    private Repository repository;

    @Spy
    @InjectMocks
    private EditorHandler editorHandler = new EditorHandler();

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    @Before
    public void setup() {
        super.setup();
        when(profileService.findProfile(any(HttpServletRequest.class), anyString())).thenReturn(profile);

        repository = new VFSRepository(producer.getIoService());
        ((VFSRepository)repository).setDescriptor(descriptor);
        profile.setRepository(repository);

        when(editorHandler.getProfile()).thenReturn(profile);
    }

    @After
    public void teardown() {
        super.teardown();
        System.clearProperty(EditorHandler.SHOW_PDF_DOC);
        System.clearProperty(EditorHandler.SERVICE_REPO);
        System.clearProperty(EditorHandler.SERVICE_REPO_TASKS);
        System.clearProperty(EditorHandler.FORMS_TYPE);
    }

    @Test
    public void testGetInstanceViewMode() throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("instanceviewmode", "false");

        assertEquals(editorHandler.getInstanceViewMode(new TestHttpServletRequest(params)), "false");

        params.put("instanceviewmode", "true");
        assertEquals(editorHandler.getInstanceViewMode(new TestHttpServletRequest(params)), "true");

        params.remove("instanceviewmode");
        assertEquals(editorHandler.getInstanceViewMode(new TestHttpServletRequest(params)), "false");
    }

    @Test
    public void testDoShowPDFDoc() throws Exception {

        TestServletConfig config = new TestServletConfig(new TestServletContext(repository));

        // no init parameter set and no system property set
        assertFalse(editorHandler.doShowPDFDoc(config));

        // init parameter set and no system property set
        config.getServletContext().setInitParameter(EditorHandler.SHOW_PDF_DOC, "false");
        assertFalse(editorHandler.doShowPDFDoc(config));

        config.getServletContext().setInitParameter(EditorHandler.SHOW_PDF_DOC, "true");
        assertTrue(editorHandler.doShowPDFDoc(config));

        // system property overwrites config init parameter
        config.getServletContext().setInitParameter(EditorHandler.SHOW_PDF_DOC, "true");
        System.setProperty(EditorHandler.SHOW_PDF_DOC, "false");
        assertFalse(editorHandler.doShowPDFDoc(config));

        config.getServletContext().setInitParameter(EditorHandler.SHOW_PDF_DOC, "false");
        System.setProperty(EditorHandler.SHOW_PDF_DOC, "true");
        assertTrue(editorHandler.doShowPDFDoc(config));

    }

    @Test
    public void testDoGetFindProfile() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        try {
            editorHandler.doGet(request, mock(HttpServletResponse.class));
        } catch (Exception e) {
            // exception thrown due to mocked request and response
        }
        verify(profileService, times(1)).findProfile(request, "jbpm");
    }

    @Test
    public void testFormsTypeViaSystemProp() {
        System.setProperty(EditorHandler.FORMS_TYPE, "form");
        assertEquals("form", editorHandler.getProfile().getFormsType());

        System.setProperty(EditorHandler.FORMS_TYPE, "frm");
        assertEquals("frm", editorHandler.getProfile().getFormsType());

        System.setProperty(EditorHandler.FORMS_TYPE, "");
        assertEquals("", editorHandler.getProfile().getFormsType());

        System.clearProperty(EditorHandler.FORMS_TYPE);
        assertNull( editorHandler.getProfile().getFormsType());
    }

    @Test
    public void testDoGetProfileAlreadySet() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        editorHandler.setProfile(profile);
        try {
            editorHandler.doGet(request, mock(HttpServletResponse.class));
        } catch (Exception e) {
            // exception thrown due to mocked request and response
        }
        verify(profileService, never()).findProfile(any(HttpServletRequest.class), anyString());
    }

    @Test
    public void testServiceRepoAndTaskSystem() throws Exception {
        System.setProperty(EditorHandler.SERVICE_REPO, "service repo");
        System.setProperty(EditorHandler.SERVICE_REPO_TASKS, "taskA,taskB");
        TestServletConfig config = new TestServletConfig(new TestServletContext(repository));

        verifyServiceRepoAndTasks(config, true);
    }

    @Test
    public void testServiceRepoAndTaskServlet() throws Exception {
        TestServletContext context = new TestServletContext(repository);
        context.setInitParameter(EditorHandler.SERVICE_REPO, "service repo");
        context.setInitParameter(EditorHandler.SERVICE_REPO_TASKS, "taskA,taskB");
        TestServletConfig config = new TestServletConfig(context);

        verifyServiceRepoAndTasks(config, true);
    }

    @Test
    public void testServiceRepoAndTaskEmpty() throws Exception {
        TestServletConfig config = new TestServletConfig(new TestServletContext(repository));
        verifyServiceRepoAndTasks(config, false);
    }

    private void verifyServiceRepoAndTasks(ServletConfig config, boolean present) throws Exception {
        PrintWriter writer = mock(PrintWriter.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(writer);

        editorHandler.init(config);
        editorHandler.doGet(mock(HttpServletRequest.class),response);

        verify(writer).write(stringCaptor.capture());

        if(present) {
            assertTrue(stringCaptor.getValue().contains("ORYX.SERVICE_REPO = \"service repo\";"));
            assertTrue(stringCaptor.getValue().contains("ORYX.SERVICE_REPO_TASKS"));
            assertTrue(stringCaptor.getValue().contains("\"name\" : \"taskA\""));
            assertTrue(stringCaptor.getValue().contains("\"name\" : \"taskB\""));
        } else {
            assertTrue(stringCaptor.getValue().contains("ORYX.SERVICE_REPO = \"\";"));
            assertFalse(stringCaptor.getValue().contains("\"name\" : \"taskA\""));
            assertFalse(stringCaptor.getValue().contains("\"name\" : \"taskB\""));
        }
    }
}
