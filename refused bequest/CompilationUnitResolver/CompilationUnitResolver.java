package org.eclipse.jdt.core.dom;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.lookup.DefaultBindingResolver;
import org.eclipse.jdt.internal.compiler.util.HashtableOfObject;
import org.eclipse.jdt.internal.compiler.lookup.BindingKeyResolver;

/**
 * Versão mínima de {@code CompilationUnitResolver}.
 *
 * 1. Faz parse diet e resolve binding simples.
 * 2. Recusa mutators herdados (Refused Bequest).
 * 3. Expõe contagem de problemas do último parse.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
class CompilationUnitResolver extends Compiler {

    private int lastProblemCount = 0;
    private final ICompilationUnit envUnit;

    CompilationUnitResolver(ICompilationUnit envUnit) {
        super(
            name -> envUnit,
            STOP_ON_FIRST_ERROR,
            new CompilerOptions(Collections.emptyMap()),
            NO_REQUESTOR,
            new DefaultProblemFactory());
        this.envUnit = envUnit;
    }

    private static final IErrorHandlingPolicy STOP_ON_FIRST_ERROR = new IErrorHandlingPolicy() {
        public boolean stopOnFirstError() { return true; }
        public boolean proceedOnErrors() { return false; }
        public boolean ignoreAllErrors() { return false; }
    };

    private static final ICompilerRequestor NO_REQUESTOR = r -> { };

    CompilationUnit parse(ICompilationUnit source, int apiLevel, Map opts) {
        CompilationUnitDeclaration decl = this.parser.dietParse(source, resultFor(source));
        if (decl != null && decl.compilationResult != null) {
            lastProblemCount = decl.compilationResult.problemCount;
        } else {
            lastProblemCount = 0;
        }
        return CompilationUnitResolver.convert(
                decl,
                source.getContents(),
                apiLevel,
                opts,
                false,
                WorkingCopyOwner.PRIMARY,
                null,
                0,
                null,
                false);
    }

    IBinding resolveBinding(String key) {
        Binding raw = new BindingKeyResolver(key, this, this.lookupEnvironment)
                .getCompilerBinding();
        if (raw == null) {
            return null;
        }
        DefaultBindingResolver resolver = new DefaultBindingResolver(
                this.lookupEnvironment,
                null,
                new DefaultBindingResolver.BindingTables(),
                false,
                false);
        return resolver.getBinding(raw);
    }

    int getLastProblemCount() {
        return lastProblemCount;
    }

    @Override
    public void initializeParser() {
        throw new UnsupportedOperationException("mutator não permitido");
    }

    @Override
    protected void beginToCompile(ICompilationUnit[] u, String[] k) {
        throw new UnsupportedOperationException("mutator não permitido");
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("mutator não permitido");
    }

    private static CompilationResult resultFor(ICompilationUnit cu) {
        return new CompilationResult(cu, 0, 1, 1);
    }
}
