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
package org.nuxeo.ecm.automation.core.operations;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.scripting.Scripting.GroovyScript;
import org.nuxeo.ecm.automation.core.scripting.Scripting.MvelScript;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;

/**
 * Run a script given as the input of the operation (as a blob).
 * 
 * Note that this operation is available only as administrator
 * 
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@Operation(id = RunInputScript.ID, category = Constants.CAT_SCRIPTING, label = "Run Input Script", description = "Run a script from the input blob. A blob comtaining script result is returned.")
public class RunInputScript {

    public static final String ID = "Context.RunInputScript";

    @Context
    protected OperationContext ctx;

    @OperationMethod
    public Blob run(Blob blob) throws Exception {
        if (!((NuxeoPrincipal) ctx.getPrincipal()).isAdministrator()) {
            throw new OperationException(
                    "Not allowed. You must be administrator to run scripts");
        }
        Object r = null;
        String fname = blob.getFilename();
        if (fname == null || fname.endsWith(".mvel")) {
            r = MvelScript.compile(blob.getString()).eval(ctx);
        } else if (fname.endsWith(".groovy")) {
            r = new GroovyScript(blob.getString()).eval(ctx);
        } else {
            throw new OperationException("Unknown scripting language for: "
                    + fname);
        }
        if (r != null) {
            StringBlob b = new StringBlob(r.toString());
            b.setMimeType("text/plain");
            b.setFilename("result");
            return b;
        }
        return null;
    }

}