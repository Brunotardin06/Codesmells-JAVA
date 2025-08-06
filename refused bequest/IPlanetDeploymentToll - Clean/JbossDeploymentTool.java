/*
 * Copyright  2001-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.tools.ant.taskdefs.optional.ejb;

import java.io.File;
import java.util.Hashtable;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

/**
 * The deployment tool to add the jboss specific deployment descriptor to the ejb jar file.
 * Jboss only requires one additional file jboss.xml and does not require any additional
 * compilation.
 *
 * @version 1.0
 * @see EjbJar#createJboss
 */
public class JbossDeploymentTool extends GenericDeploymentTool {
    private static final String JBOSS_DD        = "jboss.xml";
    private static final String JBOSS_CMP10D    = "jaws.xml";
    private static final String JBOSS_CMP20D    = "jbosscmp-jdbc.xml";

    private String jarSuffix = ".jar";

    // Mapeia versões CMP a seus arquivos
    private static final Map<EjbJar.CMPVersion, String> CMP_DESCRIPTOR_MAP = Map.of(
        EjbJar.CMPVersion.CMP1_0, JBOSS_CMP10D,
        EjbJar.CMPVersion.CMP2_0, JBOSS_CMP20D
    );

    public void setSuffix(String suffix) {
        this.jarSuffix = suffix;
    }

    protected void addVendorFiles(Hashtable<String,File> ejbFiles, String ddPrefix) {
        addDeploymentDescriptor(ejbFiles, ddPrefix);
        addCmpDescriptor(ejbFiles, ddPrefix);
    }

    private void addDeploymentDescriptor(Hashtable<String,File> ejbFiles, String ddPrefix) {
        File ddFile = locateDescriptor(ddPrefix + JBOSS_DD);
        if (ddFile != null) {
            ejbFiles.put(META_DIR + JBOSS_DD, ddFile);
        }
    }

    private void addCmpDescriptor(Hashtable<String,File> ejbFiles, String ddPrefix) {
        EjbJar.CMPVersion version = getParent().getCmpversion();
        String fileName = CMP_DESCRIPTOR_MAP.getOrDefault(version, JBOSS_CMP10D);
        File cmpFile = locateDescriptor(ddPrefix + fileName);

        if (cmpFile != null) {
            ejbFiles.put(META_DIR + fileName, cmpFile);
        }
    }

    private File locateDescriptor(String filename) {
        File f = new File(getConfig().descriptorDir, filename);
        if (!f.exists()) {
            int lvl = filename.equals(JBOSS_DD) ? Project.MSG_WARN : Project.MSG_VERBOSE;
            log("Não encontrou descriptor “" + filename + "” em: " + f.getPath(), lvl);
            return null;
        }
        return f;
    }

    protected File getVendorOutputJarFile(String baseName) {
        File dest = Optional.ofNullable(getDestDir())
                            .orElse(new File(getParent().getDestdir()));
        return new File(dest, baseName + jarSuffix);
    }

    public void validateConfigured() throws BuildException {
        // (sem mudança por ora)
    }

    private EjbJar getParent() {
        return (EjbJar) getTask();
    }
}
