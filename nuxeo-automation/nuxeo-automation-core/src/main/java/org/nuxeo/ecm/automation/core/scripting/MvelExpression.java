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
package org.nuxeo.ecm.automation.core.scripting;

import java.io.Serializable;

import org.mvel2.MVEL;
import org.nuxeo.ecm.automation.OperationContext;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class MvelExpression implements Expression {

    private static final long serialVersionUID = 1L;

//    protected static HashMap<String,Object> imports = new HashMap<String, Object>();
//    static {
//        imports.put("Date", java.util.Date.class);
//    }

    protected transient volatile Serializable compiled;
    protected String expr;


    public MvelExpression(String expr) {
        this.expr = expr;
    }

    public Object eval(OperationContext ctx) throws Exception {
        if (compiled  == null) {
            compiled = MVEL.compileExpression(expr);//, imports);
        }
        return MVEL.executeExpression(compiled, Scripting.initBindings(ctx));
    }

}