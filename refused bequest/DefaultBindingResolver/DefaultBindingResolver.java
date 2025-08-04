package org.eclipse.jdt.core.dom.refactored;

/*
 * Simplified & modular refactor of the former DefaultBindingResolver.
 * 
 * Goals
 *  1. Remove GOD CLASS smell by extracting responsibilities to helper components.
 *  2. Keep **only** the Refused Bequest smell: this class overrides one
 *     inherited method (resolveWellKnownType) but *deliberately* refuses to
 *     provide a meaningful implementation, returning null instead of honoring
 *     the expected contract. All other smells were addressed via extraction
 *     and delegation.
 *  3. Retain thread–safety guarantees by synchronizing the same façade methods
 *     the original class exposed.
 *
 *  NOTE: The helper classes below are greatly compressed stubs that illustrate
 *  the new architecture. In a production refactor you would place each helper
 *  in its own file and port over the full logic from the original 3 000‑line
 *  class, ideally covered by tests.
 */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;

/** Facade that delegates heavy‑weight logic to *cohesive* helpers. */
@SuppressWarnings({"rawtypes", "unchecked"})
class DefaultBindingResolver extends BindingResolver {

    /* =====================  Core collaborators  ======================= */

    private final BindingService bindingSvc;
    private final ConstantEvaluator constantEval;
    private final TypeInferenceUtil inferenceUtil;

    // lightweight caches (moved out of the main class)
    private final Map<Object, ASTNode> newToOld = new ConcurrentHashMap<>();

    /* ==========================  CTORs  ============================== */
    DefaultBindingResolver(CompilationUnitScope scope,
                           WorkingCopyOwner owner,
                           BindingTables tables,
                           boolean recovering,
                           boolean fromProject) {
        super(scope, owner, tables, recovering, fromProject);
        this.bindingSvc     = new BindingService(scope, tables, recovering);
        this.constantEval   = new ConstantEvaluator(scope);
        this.inferenceUtil  = new TypeInferenceUtil(scope);
    }

    DefaultBindingResolver(LookupEnvironment env,
                           WorkingCopyOwner owner,
                           BindingTables tables,
                           boolean recovering,
                           boolean fromProject) {
        this(new CompilationUnitScope(new CompilationUnitDeclaration(null, null, -1), env),
             owner, tables, recovering, fromProject);
    }

    /* =====================  Delegated operations  ===================== */

    @Override
    synchronized ASTNode findDeclaringNode(IBinding binding) {
        return bindingSvc.findDeclaringNode(binding);
    }

    @Override
    synchronized ASTNode findDeclaringNode(String key) {
        return bindingSvc.findDeclaringNode(key);
    }

    @Override
    synchronized ITypeBinding resolveExpressionType(Expression expr) {
        return inferenceUtil.resolveExpressionType(expr, newToOld);
    }

    @Override
    synchronized Object resolveConstantExpressionValue(Expression expr) {
        return constantEval.valueOf(expr, newToOld);
    }


    @Override
    synchronized ITypeBinding resolveWellKnownType(String name) {
        return null; // <- REFUSED BEQUEST: contract is ignored on purpose
    }

    /* ===========================  Helpers  ============================ */

    /** Extracted cohesive service for binding lookups & caching. */
    private static final class BindingService {
        private final CompilationUnitScope scope;
        private final BindingTables tables;
        private final boolean recovering;
        private final Map<Object, org.eclipse.jdt.core.dom.ASTNode> bindingsToAst = new ConcurrentHashMap<>();

        BindingService(CompilationUnitScope scope, BindingTables tables, boolean recovering) {
            this.scope      = scope;
            this.tables     = tables;
            this.recovering = recovering;
        }

        ASTNode findDeclaringNode(IBinding binding) {
            if (binding == null) return null;
            // greatly simplified logic
            return (ASTNode) bindingsToAst.get(binding.getDeclaringMember());
        }

        ASTNode findDeclaringNode(String key) {
            if (key == null) return null;
            Object b = tables.bindingKeysToBindings.get(key);
            return (ASTNode) bindingsToAst.get(b);
        }

        // Additional lookup methods would be ported here …
    }

    /** Lightweight constant evaluation (extracted). */
    private static final class ConstantEvaluator {
        private final CompilationUnitScope scope;
        ConstantEvaluator(CompilationUnitScope scope) { this.scope = scope; }
        Object valueOf(Expression expr, Map<Object, ASTNode> map) {
            try {
                org.eclipse.jdt.internal.compiler.ast.ASTNode node =
                    (org.eclipse.jdt.internal.compiler.ast.ASTNode) map.get(expr);
                // minimal: if not literal, bail out
                if (!(node instanceof org.eclipse.jdt.internal.compiler.ast.Expression)) return null;
                var c = ((org.eclipse.jdt.internal.compiler.ast.Expression) node).constant;
                return (c == null || c == Constant.NotAConstant) ? null : c.constantValue();
            } catch (AbortCompilation e) {
                return null;
            }
        }
    }

    /** Isolated utility for type‑inference related helpers. */
    private static final class TypeInferenceUtil {
        private final CompilationUnitScope scope;
        TypeInferenceUtil(CompilationUnitScope scope) { this.scope = scope; }
        ITypeBinding resolveExpressionType(Expression expr, Map<Object, ASTNode> map) {
            try {
                var node = (org.eclipse.jdt.internal.compiler.ast.ASTNode) map.get(expr);
                if (node instanceof org.eclipse.jdt.internal.compiler.ast.Expression)
                    return new TypeBinding(null, ((org.eclipse.jdt.internal.compiler.ast.Expression) node).resolvedType);
            } catch (AbortCompilation e) {
                // ignore
            }
            return null;
        }
    }
}
