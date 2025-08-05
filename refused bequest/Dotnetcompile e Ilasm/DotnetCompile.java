package org.apache.tools.ant.taskdefs.optional.dotnet;

import org.apache.tools.ant.*;
import org.apache.tools.ant.types.*;

import java.io.File;
import java.util.*;
import java.util.function.*;


public abstract class DotnetCompile extends DotnetBaseMatchingTask {

    
    private final CompileOptions options = new CompileOptions();

    public void setReferences(String r)                { options.references(r); }
    public void addReference(FileSet fs)               { options.referenceSet(fs); }
    public void addDefine(DotnetDefine d)              { options.define(d); }
    public void addResource(DotnetResource r)          { options.resource(r); }
    public void setExecutable(String exe)              { options.executable(exe); }
    public void setDebug(boolean f)                    { options.debug(f); }
    public void setOptimize(boolean f)                 { options.optimize(f); }
    public void setWarnLevel(int n)                    { options.warnLevel(n); }
    public void setTargetType(TargetTypes t)           { options.targetType(t.getValue()); }
    public void setMainClass(String c)                 { options.mainClass(c); }
    public void setExtraOptions(String x)              { options.extra(x); }
    public void setAdditionalModules(String m)         { options.additionalModules(m); }
    public void setUtf8Output(boolean f)               { options.utf8Output(f); }
    public void setIncludeDefaultReferences(boolean f) { options.includeDefaultRefs(f); }
    public void setWin32Icon(File f)                   { options.win32Icon(f); }
    public void setWin32Res(File f)                    { options.win32Res(f); }
    public void setFailOnError(boolean f)              { options.failOnError(f); }

    
    @Override public void setDestDir(File ignored) {
        log("DestDir não é utilizado por DotnetCompile", Project.MSG_VERBOSE);
    }


    @Override public void execute() throws BuildException {
        options.validate();

        NetCommand cmd = new NetCommand(this, getTaskName(), options.executable());
        options.applyTo(cmd, this);                     // switches comuns

        ScanContext ctx = new ScanContext(
                cmd,
                getProject(),
                getOutputFileTimestamp(),
                getReferenceDelimiter());

        int outdated = options.scanReferenceSets(ctx);  // dependências

        addCompilerSpecificOptions(cmd);                // hook específico
        addFilesAndExecute(cmd, outdated > 0);
    }

  
    protected abstract void addCompilerSpecificOptions(NetCommand cmd);
    protected abstract String  getReferenceDelimiter();
    protected abstract String  getFileExtension();
    protected abstract String  createResourceParameter(DotnetResource r);

    
    //3. Builder + Modelo Open/Closed                                    
    
    protected static final class CompileOptions {

        private final List<Option> opts = new ArrayList<>();
        private String executable;                                   // obrigatório

        // ---------- setters fluentes ---------- 
        CompileOptions executable(String e){ executable=e; return this; }
        CompileOptions references(String r){ return add(new Value("/reference:", r)); }
        CompileOptions referenceSet(FileSet fs){ return add(new ReferenceSet(fs)); }
        CompileOptions define(DotnetDefine d){ return add(new Value("/d:", d.getValue())); }
        CompileOptions resource(DotnetResource r){ return add(new ResourceOpt(r)); }
        CompileOptions debug(boolean f){ return add(new Flag("/debug", f)); }
        CompileOptions optimize(boolean f){ return add(new Flag("/optimize", f)); }
        CompileOptions warnLevel(int n){ return add(new Value("/warn:", n)); }
        CompileOptions targetType(String t){ return add(new Value("/target:", t)); }
        CompileOptions mainClass(String c){ return add(new Value("/main:", c)); }
        CompileOptions extra(String x){ return add(new Raw(x)); }
        CompileOptions additionalModules(String m){ return add(new Value("/addmodule:", m)); }
        CompileOptions utf8Output(boolean f){ return add(new Flag("/utf8output", f)); }
        CompileOptions includeDefaultRefs(boolean f){ return add(new Flag("/nostdlib", !f)); }
        CompileOptions win32Icon(File f){ return add(new FileValue("/win32icon:", f)); }
        CompileOptions win32Res(File f){ return add(new FileValue("/win32res:", f)); }
        CompileOptions failOnError(boolean f){ return add(cmd -> cmd.setFailOnError(f)); }

        //---------- comportamento ---------- 
        void validate(){
            if (executable == null)
                throw new BuildException("Executable não definido");
        }
        String executable(){ return executable; }

        void applyTo(NetCommand cmd, DotnetCompile owner){
            cmd.addArgument("/nologo");
            opts.forEach(o -> o.apply(cmd, owner));
        }

        int scanReferenceSets(ScanContext ctx){
            return opts.stream()
                       .filter(ReferenceSet.class::isInstance)
                       .map(ReferenceSet.class::cast)
                       .mapToInt(r -> r.applyAndReturnOutdated(ctx))
                       .sum();
        }

        private CompileOptions add(Option o){ opts.add(o); return this; }
    }

    
    //4. Hierarquia de Option                                            
    
    private interface Option { void apply(NetCommand c, DotnetCompile owner); }

    private static final class Value implements Option {
        private final String prefix; private final Object val;
        Value(String p,Object v){ prefix=p; val=v; }
        public void apply(NetCommand c, DotnetCompile o){
            if(val!=null) c.addArgument(prefix+val);
        }
    }
    private static final class Raw implements Option {
        private final String arg; Raw(String a){ arg=a; }
        public void apply(NetCommand c,DotnetCompile o){
            if(arg!=null && !arg.isEmpty()) c.addArgument(arg);
        }
    }
    private static final class Flag implements Option {
        private final String flag; private final boolean on;
        Flag(String f,boolean o){ flag=f; on=o; }
        public void apply(NetCommand c,DotnetCompile o){
            if(on) c.addArgument(flag+(flag.endsWith("+")||flag.endsWith("-")?"":"+"));
        }
    }
    private static final class FileValue implements Option {
        private final String prefix; private final File file;
        FileValue(String p,File f){ prefix=p; file=f; }
        public void apply(NetCommand c,DotnetCompile o){
            if(file!=null) c.addArgument(prefix+file);
        }
    }
    private static final class ResourceOpt implements Option {
        private final DotnetResource res;
        ResourceOpt(DotnetResource r){ res=r; }
        public void apply(NetCommand c,DotnetCompile o){
            String p=o.createResourceParameter(res);
            if(p!=null) c.addArgument(p);
        }
    }
    private static final class ReferenceSet implements Option {
        private final FileSet set;
        private Set<File> files;
        ReferenceSet(FileSet s){ set=s; }
        public void apply(NetCommand c,DotnetCompile o){ /* emissão posterior */ }

        int applyAndReturnOutdated(ScanContext ctx){
            materialise(ctx.project());
            int outdated=0;
            StringJoiner sj=new StringJoiner(ctx.delimiter(), "/reference:", "");
            for(File f:files){
                sj.add(f.getPath());
                if(f.lastModified()>ctx.outputTimestamp()) outdated++;
            }
            ctx.cmd().addArgument(sj.toString());
            return outdated;
        }
        private void materialise(Project p){
            if(files!=null) return;
            files=new LinkedHashSet<>();
            for(String inc:set.getDirectoryScanner(p).getIncludedFiles()){
                File f=new File(set.getDir(p),inc);
                if(isManagedBinary(f)) files.add(f);
            }
        }
        private static boolean isManagedBinary(File f){
            String n=f.getName().toLowerCase(Locale.ROOT);
            return n.endsWith(".exe")||n.endsWith(".dll")||n.endsWith(".netmodule");
        }
    }

    
    private record ScanContext(NetCommand cmd,
                               Project     project,
                               long        outputTimestamp,
                               String      delimiter) { }

   
    public static class TargetTypes extends EnumeratedAttribute {
        @Override public String[] getValues(){
            return new String[]{"exe","library","module","winexe"};
        }
    }
}
