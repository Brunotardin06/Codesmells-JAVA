package org.apache.tools.ant.taskdefs.optional.ejb;

import java.io.File;
import java.util.Hashtable;
import javax.xml.parsers.SAXParser;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.xml.sax.SAXException;

/**
 * Deployment helper for iPlanet Application Server 6.x.
 * Only the behaviour essential to jar assembly is preserved;
 * configuration checks and descriptor parsing are deliberately
 * skipped (refused‑bequest).  
 */
public class IPlanetDeploymentTool extends GenericDeploymentTool {

    /* ---------- configurable attributes ---------- */

    private File    iasHome;
    private String  jarSuffix = ".jar";
    private boolean keepGenerated;
    private boolean debug;

    /* ---------- per‑descriptor state ---------- */

    private String descriptorName;
    private String iasDescriptorName;

    /* ---------- constants ---------- */

    private static final String IAS_DD = "ias-ejb-jar.xml";

    /* ---------- Ant attribute setters ---------- */

    public void setIashome       (File    dir)  { iasHome       = dir; }
    public void setSuffix        (String  s  )  { jarSuffix     = s;  }
    public void setKeepgenerated (boolean on )  { keepGenerated = on; }
    public void setDebug         (boolean on )  { debug         = on; }

    /* generic‑Jar suffix is meaningless for iAS */
    public void setGenericJarSuffix(String ignored) {
        log("genericjarsuffix ignored for iPlanet", Project.MSG_WARN);
    }

    /* ---------- core task flow ---------- */

    public void processDescriptor(String dd, SAXParser parser) {
        descriptorName    = dd;
        iasDescriptorName = null;     // reset cache
        log("processing " + dd + " → " + getIasDescriptorName(),
            Project.MSG_VERBOSE);
        super.processDescriptor(dd, parser);
    }

    protected void checkConfiguration(String f, SAXParser p) throws BuildException { /* no‑op */ }
    protected Hashtable parseEjbFiles(String f, SAXParser p) throws SAXException { return new Hashtable(); }

    /* ---------- jar assembly helpers ---------- */

    protected void addVendorFiles(Hashtable files, String ignored) {
        files.put(META_DIR + IAS_DD,
                  new File(getConfig().descriptorDir, getIasDescriptorName()));
    }

    File getVendorOutputJarFile(String base) {
        return new File(getDestDir(), base + jarSuffix);
    }

    protected String getPublicId() { return null; }

    /* ---------- private helpers ---------- */

    private String getIasDescriptorName() {
        if (iasDescriptorName != null) return iasDescriptorName;

        int slash = descriptorName.lastIndexOf(File.separatorChar) + 1;
        String path   = slash > 0 ? descriptorName.substring(0, slash) : "";
        String simple = descriptorName.substring(slash);
        int   dot     = simple.lastIndexOf('.');
        String base   = dot > 0 ? simple.substring(0, dot) : simple;
        iasDescriptorName = path + base + "-ias.xml";
        return iasDescriptorName;
    }
}
