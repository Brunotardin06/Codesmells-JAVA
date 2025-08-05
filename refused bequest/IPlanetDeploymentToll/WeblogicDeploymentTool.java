/*
 *  Simplified version focused on WebLogic-specific behaviour.
 *  * All configuration is grouped in WLEjbConfig            ← eliminates long parameter lists
 *  * Interaction with ‘ejbc’ is isolated in EjbcInvoker     ← extracts a separate concern
 *  * WeblogicDeploymentTool itself just orchestrates work   ← reduces class size
 *
 *  Refused-Bequest: toda a funcionalidade da superclasse que trata de
 *  servidores genéricos permanece herdada mas não utilizada aqui.
 */
package org.apache.tools.ant.taskdefs.optional.ejb;

import org.apache.tools.ant.*;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.*;
import org.apache.tools.ant.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;

/** Orchestrates the build of WebLogic EJB jars. */
public class WeblogicDeploymentTool extends GenericDeploymentTool {

    /*---—————————— configuration object ——————————---*/
    public static class WLEjbConfig {
        String  jarSuffix            = ".jar";
        boolean keepGeneratedSources = false;
        boolean keepGenericJar       = false;
        boolean alwaysRebuild        = true;
        boolean runEjbc              = true;

        String  ejbcClassName;
        String  additionalArgs       = "";
        String  additionalJvmArgs    = "";
        String  compiler             = "default";
        Integer jvmDebugLevel        = null;

        Path    wlClasspath;
        Path    combinedClasspath;
        List<Environment.Variable> sysProps = new ArrayList<>();
        File    outputDir;
    }

    private final WLEjbConfig cfg = new WLEjbConfig();             // single parameter-object

    /* helpers */
    private static final FileUtils FILES = FileUtils.getFileUtils();

    /*---—————————— public ‘setter’ façade ——————————---*/
    public Path createWLClasspath()                     { return cfg.wlClasspath  = optPath(cfg.wlClasspath); }
    public void addSysproperty(Environment.Variable v)  { cfg.sysProps.add(v); }
    public void setJvmargs(String v)                    { cfg.additionalJvmArgs  = v; }
    public void setArgs(String v)                       { cfg.additionalArgs     = v; }
    public void setCompiler(String v)                   { cfg.compiler           = v; }
    public void setJvmDebugLevel(Integer v)             { cfg.jvmDebugLevel      = v; }
    public void setSuffix(String v)                     { cfg.jarSuffix          = v; }
    public void setKeepgenerated(String v)              { cfg.keepGeneratedSources = Boolean.parseBoolean(v); }
    public void setKeepgeneric(boolean v)               { cfg.keepGenericJar     = v; }
    public void setRebuild(boolean v)                   { cfg.alwaysRebuild      = v; }
    public void setNoEJBC(boolean v)                    { cfg.runEjbc            = !v; }
    public void setOutputDir(File d)                    { cfg.outputDir          = d; }
    public void setEjbcClass(String c)                  { cfg.ejbcClassName      = c; }

    private Path optPath(Path p) { return p == null ? new Path(getTask().getProject()) : p; }

    /*---—————————— jar-writing high level steps ——————————---*/
    @Override
    protected void writeJar(String base, File wlJar, Hashtable files, String publicId) {
        File genericJar = super.getVendorOutputJarFile(base);       // cria JAR genérico
        super.writeJar(base, genericJar, files, publicId);

        if (cfg.alwaysRebuild || needsRebuild(genericJar, wlJar)) {
            if (cfg.runEjbc)
                new EjbcInvoker(cfg, getTask()).invoke(genericJar, wlJar, publicId);
            else
                copyAsIs(genericJar, wlJar);
        }
        if (!cfg.keepGenericJar) genericJar.delete();
    }

    /*---—————————— small helpers ——————————---*/
    private void copyAsIs(File src, File dst) {
        try { FILES.copyFile(src, dst); }
        catch (IOException ex) { throw new BuildException("Copy failed", ex); }
        if (!cfg.keepGeneratedSources) src.delete();
    }
    private boolean needsRebuild(File genericJar, File wlJar) {
        return !(genericJar.exists() && wlJar.exists())
               || genericJar.lastModified() > wlJar.lastModified();
    }

    /*---—————————— EjbcInvoker & declarative options ——————————---*/
    private interface EjbcOption { void apply(Java cmd, WLEjbConfig c); }

    private static final class KeepGeneratedOpt implements EjbcOption {
        public void apply(Java j, WLEjbConfig c){
            if (c.keepGeneratedSources) j.createArg().setValue("-keepgenerated");
        }
    }
    private static final class CompilerOpt implements EjbcOption {
        public void apply(Java j, WLEjbConfig c){
            if (!"default".equals(c.compiler)) {
                j.createArg().setValue("-compiler");
                j.createArg().setLine(c.compiler);
            }
        }
    }
    private static final class ExtraArgsOpt implements EjbcOption {
        public void apply(Java j, WLEjbConfig c){
            if (!c.additionalArgs.isEmpty()) j.createArg().setLine(c.additionalArgs);
        }
    }
    private static final class ClasspathOpt implements EjbcOption {
        public void apply(Java j, WLEjbConfig c){
            if (c.wlClasspath != null && c.combinedClasspath != null
                    && !c.combinedClasspath.toString().trim().isEmpty()) {
                j.createArg().setValue("-classpath");
                j.createArg().setPath(c.combinedClasspath);
            }
        }
    }

    private static final class EjbcInvoker {
        private static final List<EjbcOption> OPTIONS = List.of(
            new KeepGeneratedOpt(),
            new CompilerOpt(),
            new ExtraArgsOpt(),
            new ClasspathOpt()
        );

        private final WLEjbConfig c;   private final Task task;
        EjbcInvoker(WLEjbConfig c, Task t){ this.c=c; this.task=t; }

        void invoke(File src, File dst, String publicId){
            Java j = new Java(task); j.setTaskName("ejbc");
            if (!c.additionalJvmArgs.isEmpty())
                j.createJvmarg().setLine(c.additionalJvmArgs);
            c.sysProps.forEach(j::addSysproperty);
            if (c.jvmDebugLevel != null)
                j.createJvmarg().setLine("-Dweblogic.StdoutSeverityLevel="+c.jvmDebugLevel);

            j.setClassname(resolveEjbcClass(publicId));

            /* aplica opções declarativas */
            OPTIONS.forEach(opt -> opt.apply(j, c));

            j.createArg().setValue(src.getPath());
            j.createArg().setValue(c.outputDir==null ? dst.getPath() : c.outputDir.getPath());

            j.setFork(true);
            j.setClasspath(Optional.ofNullable(c.wlClasspath).orElse(c.combinedClasspath));

            if (j.executeJava() != 0)
                throw new BuildException("ejbc returned error");
        }

        private String resolveEjbcClass(String id){
            if (c.ejbcClassName != null) return c.ejbcClassName;
            if (PUBLICID_EJB11.equals(id)) return COMPILER_EJB11;
            if (PUBLICID_EJB20.equals(id)) return COMPILER_EJB20;
            task.log("Unknown publicId "+id+" – defaulting to EJB11 compiler", Project.MSG_WARN);
            return COMPILER_EJB11;
        }
    }
}

