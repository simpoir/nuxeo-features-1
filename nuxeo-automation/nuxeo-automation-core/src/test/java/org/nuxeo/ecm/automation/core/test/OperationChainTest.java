/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.ecm.automation.core.test;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.InvalidChainException;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.operations.FetchContextDocument;
import org.nuxeo.ecm.automation.core.operations.SetVar;
import org.nuxeo.ecm.automation.core.scripting.MvelExpression;
import org.nuxeo.ecm.automation.core.scripting.MvelTemplate;
import org.nuxeo.ecm.automation.core.scripting.Scripting;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.SimplePrincipal;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import com.google.inject.Inject;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.automation.core")
@LocalDeploy("org.nuxeo.ecm.automation.core:test-operations.xml")
public class OperationChainTest {

    protected DocumentModel src;

    @Inject
    AutomationService service;

    @Inject
    CoreSession session;

    @Before
    public void initRepo() throws Exception {
        src = session.createDocumentModel("/", "src", "Folder");
        src.setPropertyValue("dc:title", "Source");
        src = session.createDocument(src);
        session.save();
        src = session.getDocument(src.getRef());
    }

    @After
    public void clearRepo() throws Exception {
        session.removeChildren(session.getRootDocument().getRef());
    }

    protected void assertContextOk(OperationContext ctx, String chain, String message, String title) {
        Assert.assertEquals(chain, ctx.get("chain"));
        Assert.assertEquals(message, ctx.get("message"));
        Assert.assertEquals(title, ctx.get("title"));
    }

    // ------ Tests comes here --------

    /**
     * <pre>
     * Input: doc
     *
     * Test chain: O1 -> O2 -> O1
     * O1:doc:doc | O2:doc:ref* | O1:doc:doc
     * O1:ref:doc | O2:doc:doc  | O1:ref:doc
     *
     * where * means the method has priority over the other with the same input.
     *
     * Expected output: (parameters in context)
     * chain : "O1:doc:doc,O2:doc:ref,O1:ref:doc"
     * message : "Hello 1!,Hello 2!,Hello 3!"
     * title : "Source,Source,Source"
     * </pre>
     *
     * <p>
     * This is testing a chain having multiple choices and one choice having a higher priority.
     */
    @Test public void testChain1() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        OperationChain chain = new OperationChain("testChain");
        chain.add(FetchContextDocument.ID);
        chain.add("o1").set("message", "Hello 1!");
        chain.add("o2").set("message", "Hello 2!");
        chain.add("o1").set("message", "Hello 3!");

        service.run(ctx, chain);

        assertContextOk(ctx,
                "O1:doc:doc,O2:doc:ref,O1:ref:doc",
                "Hello 1!,Hello 2!,Hello 3!",
                "Source,Source,Source");
    }

    /**
     * Same as before but use a managed chain
     * @throws Exception
     */
    @Test public void testManagedChain1() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        service.run(ctx, "mychain");

        assertContextOk(ctx,
                "O1:doc:doc,O2:doc:ref,O1:ref:doc",
                "Hello 1!,Hello 2!,Hello 3!",
                "Source,Source,Source");
    }

    /**
     * Test compiled chain
     * @throws Exception
     */
    @Test public void testManagedChain2() throws Exception {
        testManagedChain1();
    }

    /**
     * <pre>
     * Input: ref
     *
     * Test chain: O1 -> O2 -> O1
     * O1:doc:doc | O2:doc:ref* | O1:doc:doc
     * O1:ref:doc | O2:doc:doc  | O1:ref:doc
     *
     * where * means the method has priority over the other with the same input.
     *
     * Expected output: (parameters in context)
     * chain : "O1:ref:doc,O2:doc:ref,O1:ref:doc"
     * message : "Hello 1!,Hello 2!,Hello 3!"
     * title : "Source,Source,Source"
     * </pre>
     *
     * <p>
     * This test is using the same chain as in the previous test but changes the input to DocumentRef.
     * This is also testing matching on derived classes (since the IdRef used is a subclass of DocumentRef)
     */
    @Test public void testChain2() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src.getRef());

        OperationChain chain = new OperationChain("testChain");
        chain.add("o1").set("message", "Hello 1!");
        chain.add("o2").set("message", "Hello 2!");
        chain.add("o1").set("message", "Hello 3!");

        service.run(ctx, chain);

        assertContextOk(ctx,
                "O1:ref:doc,O2:doc:ref,O1:ref:doc",
                "Hello 1!,Hello 2!,Hello 3!",
                "Source,Source,Source");
    }


    /**
     * <pre>
     * Input: doc
     *
     * Test chain: O1 -> O3 -> O3
     * O1:doc:doc | O3:doc:ref  | O3:doc:ref
     * O1:ref:doc | O3:doc:doc  | O3:doc:doc
     *
     * Expected output: (parameters in context)
     * chain : "O1:doc:doc,O3:doc:doc,O3:doc:doc"
     * message : "Hello 1!,Hello 2!,Hello 3!"
     * title : "Source,Source,Source"
     * </pre>
     *
     * <p>
     * This is testing a chain having multiple choices. You can see that the second operation in chain (O3)
     * provides 2 ways of processing a 'doc'. But the 'O3:doc:doc' way will be selected since the other way
     * (e.g. O3:doc:ref) cannot generate a complete chain path.
     */
    @Test public void testChain3() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        OperationChain chain = new OperationChain("testChain");
        chain.add("o1").set("message", "Hello 1!");
        chain.add("o3").set("message", "Hello 2!");
        chain.add("o3").set("message", "Hello 3!");

        service.run(ctx, chain);

        assertContextOk(ctx,
                "O1:doc:doc,O3:doc:doc,O3:doc:doc",
                "Hello 1!,Hello 2!,Hello 3!",
                "Source,Source,Source");
    }

    /**
     * Same as before but with a ctrl operation between o3 and o3
     * @throws Exception
     */
    @Test public void testChain3WithCtrl() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        OperationChain chain = new OperationChain("testChain");
        chain.add("o1").set("message", "Hello 1!");
        chain.add("o3").set("message", "Hello 2!");
        chain.add("ctrl").set("message", "Control!");
        chain.add("o3").set("message", "Hello 3!");

        service.run(ctx, chain);

        assertContextOk(ctx,
                "O1:doc:doc,O3:doc:doc,ctrl:void:void,O3:doc:doc",
                "Hello 1!,Hello 2!,Control!,Hello 3!",
                "Source,Source,Source,Source");
    }

    /**
     * This is testing the parameter expressions.
     * If you set an operation parameter that point to 'var:principal' it will return
     * @throws Exception
     */
    @Test public void testExpressionParams() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);
        SimplePrincipal principal = new SimplePrincipal("Hello from Context!");
        ctx.put("messageHolder", principal);
        OperationChain chain = new OperationChain("testChain");
        chain.add("o1").set("message", new MvelExpression("Context[\"messageHolder\"].name"));

        service.run(ctx, chain);

        assertContextOk(ctx,
                "O1:doc:doc",
                "Hello from Context!",
                "Source");
    }

    /**
     * Same as previous but test params specified as Mvel templates
     * @throws Exception
     */
    @Test public void testTemplateParams() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);
        SimplePrincipal principal = new SimplePrincipal("Context");
        ctx.put("messageHolder", principal);
        OperationChain chain = new OperationChain("testChain");
        chain.add("o1").set("message", new MvelTemplate("Hello from @{Context[\"messageHolder\"].name}!"));

        service.run(ctx, chain);

        assertContextOk(ctx,
                "O1:doc:doc",
                "Hello from Context!",
                "Source");
    }
    /**
     * This is testing an invalid chain. The last operation in the chain accepts as input only Principal
     * which is never produced by the previous operations in the chain.
     * @throws Exception
     */
    @Test public void testInvalidChain() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        OperationChain chain = new OperationChain("testChain");
        chain.add("o1").set("message", "Hello 1!");
        chain.add("o2").set("message", "Hello 2!");
        chain.add("unmatched");

        try {
            service.run(ctx, chain);
            Assert.fail("Invalid chain not detected!");
        } catch (InvalidChainException e) {
            // test passed
        }
    }

    /**
     * When using a null context input an exception must be thrown if the first operation
     * doesn't accept void input.
     * @throws Exception
     */
    @Test public void testInvalidInput() throws Exception {
        OperationContext ctx = new OperationContext(session);

        OperationChain chain = new OperationChain("testChain");
        chain.add("o1").set("message", "Hello 1!");

        try {
            service.run(ctx, chain);
            Assert.fail("Null input was handled by a non void operation!");
        } catch (OperationException e) {
            // test passed
        }
    }

    /**
     * <pre>
     * Input: VOID
     *
     * Test chain: V1 -> V1 -> V2
     * V1:void:doc | V1:void:doc | V2:void:doc
     * V1:doc:doc  | V1:doc:doc  | V2:string:doc
     *
     * Expected output: (parameters in context)
     * chain : "V1:void:doc,V1:doc:doc,V2:void:doc"
     * message : "Hello 1!,Hello 2!,Hello 3!"
     * title : ",/,/"
     * </pre>

     * Test void input.
     * @throws Exception
     */
    @Test public void testVoidInput() throws Exception {
        OperationContext ctx = new OperationContext(session);

        OperationChain chain = new OperationChain("testChain");
        chain.add("v1").set("message", "Hello 1!");
        chain.add("v1").set("message", "Hello 2!");
        chain.add("v2").set("message", "Hello 3!");

        service.run(ctx, chain);

        assertContextOk(ctx,
                "V1:void:doc,V1:doc:doc,V2:void:doc",
                "Hello 1!,Hello 2!,Hello 3!",
                ",/,/");
    }

    /**
     * <pre>
     * Input: VOID
     *
     * Test chain: A1 -> A1
     * A1:void:docref | A1:void:docref
     * A1:doc:doc     | A1:doc:doc
     *
     * Expected output: (parameters in context)
     * chain : "A1:void:docref,A1:doc:doc"
     * message : "Hello 1!,Hello 2!"
     * title : "/,/"
     * </pre>

     * Test docref to doc adapter. Test precedence of adapters over void.
     * @throws Exception
     */
    @Test public void testTypeAdapters() throws Exception {
        OperationContext ctx = new OperationContext(session);

        OperationChain chain = new OperationChain("testChain");
        chain.add("a1").set("message", "Hello 1!");
        chain.add("a1").set("message", "Hello 2!");

        service.run(ctx, chain);

        assertContextOk(ctx,
                "A1:void:docref,A1:doc:doc",
                "Hello 1!,Hello 2!",
                "/,/");
    }

    /**
     * <pre>
     * Input: doc
     *
     * Test chain: A2
     * A2:void:docref
     * A2:docref:doc
     *
     * Expected output: (parameters in context)
     * chain : "A2:docref:docref"
     * message : "Hello 1!"
     * title : "/"
     * </pre>

     * Test doc to docref adapter.
     * @throws Exception
     */
    @Test public void testTypeAdapters2() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(session.getRootDocument());

        OperationChain chain = new OperationChain("testChain");
        chain.add("a2").set("message", "Hello 1!");

        service.run(ctx, chain);

        assertContextOk(ctx,
                "A2:docref:docref",
                "Hello 1!",
                "/");
    }

    /**
     * This is testing optional parameters. Operation2 has an optional 'message' parameter.
     * If this is not specified in the operation parameter map the default value will be used
     * which is 'default message'.
     *
     * @throws Exception
     */
    @Test public void testOptionalParam() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        OperationChain chain = new OperationChain("testChain");
        chain.add("o2");

        service.run(ctx, chain);

        assertContextOk(ctx,
                "O2:doc:ref",
                "default message",
                "Source");
    }

    /**
     * This is testing required parameters. Operation1 has a required 'message' parameter.
     * If this is not specified in the operation parameter map an exception must be thrown.
     *
     * @throws Exception
     */
    @Test public void testRequiredParam() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        OperationChain chain = new OperationChain("testChain");
        chain.add("o1");

        try {
            service.run(ctx, chain);
            Assert.fail("Required parameter test failure!");
        } catch (OperationException e) {
            // test passed
        }
    }

    /**
     * This is testing adapters when injecting parameters.
     * Operation 4 is taking a DocmentModel parameter. We will inject a DocumentRef
     * to test DocRef to DocModel adapter.
     *
     * @throws Exception
     */
    @Test public void testAdaptableParam() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        OperationChain chain = new OperationChain("testChain");
        chain.add("o4").set("message", "Hello 1!").set("doc", src.getRef());

        Object out = service.run(ctx, chain);
        Assert.assertEquals(src.getId(), ((DocumentModel)out).getId());

        assertContextOk(ctx,
                "O4:void:doc",
                "Hello 1!",
                "Source");
    }

    /**
     * Set a context variable from the title of the input document and use it in the next operation (by returning it)
     * This is also testing boolean injection.
     * @throws Exception
     */
    @Test public void testSetVar() throws Exception {
        OperationContext ctx = new OperationContext(session);
        ctx.setInput(src);

        OperationChain chain = new OperationChain("testChain");
        chain.add(SetVar.ID).set("name", "myvar")
            .set("value", Scripting.newExpression("Document['dc:title']"));
        chain.add("GetVar").set("name", "myvar").set("flag", true);

        Object out = service.run(ctx, chain);
        Assert.assertEquals(src.getTitle(), out);
    }
}