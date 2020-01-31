package com.itzhai.annotation.process.demo;

import com.sun.source.util.Trees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssert;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * 注意,此例使用到了sun.tools中的类,可能会导致不稳定.
 * 开发者不应该调用sun包，Oracle一直在提醒开发者，调用sun.*包里面的方法是危险的。
 * sun包并不包含在Java平台的标准中，它与操作系统相关，
 * 在不同的操作系统如Solaris，Windows，Linux，Mac等中的实现也各不相同，并且可能随着JDK版本而变化。详细说明:
 * http://www.oracle.com/technetwork/java/faq-sun-packages-142232.html
 *
 * Created by arthinking on 30/1/2020.
 */
@SupportedAnnotationTypes("com.itzhai.annotation.process.demo.ForceAssertions")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ForceAssertionsProcessor extends AbstractProcessor {

    // 计数器用于向用户报告已应用的替换次数
    private int tally;

    // Trees JSR269的工具类,连接程序元素和树节点的桥梁。
    // 例如，给定一个method元素，我们可以获得其关联的AST树节点
    private Trees trees;

    // TreeMaker 编译器的内部组件,用于创建树节点的工厂
    private TreeMaker make;

    // Name.Table 编译器的一个内部组件, Name是内部编译器字符串的抽象。
    // 出于效率原因，Javac使用存储在公共大型缓冲区中的哈希字符串。
    private Names names;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        trees = Trees.instance(env);
        // 我们使用处理环境来处理必要的编译器组件。在编译器内，对编译器的每次调用都使用单个处理环境（或context上下文，内部称为上下文）。
        // 把JSR269的ProcessingEnvironment转换为实际的编译器类型JavacProcessingEnvironment,以便能够调用更多的内部方法
        JavacProcessingEnvironment javacProcessingEnvironment = (JavacProcessingEnvironment)env;
        // 使用context上下文来确保每个编译器调用都存在每个编译器组件的单个副本。
        Context context = javacProcessingEnvironment.getContext();
        //  在编译器中，我们仅使用 Component.instance(context) 来获取对该阶段的引用
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        // tally 计数器用于向用户报告已应用的替换次数。
        tally = 0;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                              RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            // 遍历所有的程序元素,并且重写每个类的AST
            Set<? extends Element> elements = roundEnv.getRootElements();
            for (Element each : elements) {
                if (each.getKind() == ElementKind.CLASS) {
                    // 把JSR269的 Tree 转换为实际的JCTree类型,以便可以访问所有的AST元素。
                    JCTree tree = (JCTree) trees.getTree(each);
                    // 通过对TreeTranslator进行子类化来完成树翻译，
                    // TreeTranslator本身是TreeVisitor的子类。
                    // 这些类都不是JSR269的一部分，而是Java编译器内部的类。
                    TreeTranslator visitor = new Inliner();
                    tree.accept(visitor);
                }
            }
        } else {
            // 输出处理的断言语句的数量
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE, tally + " assertions inlined.");
        }
        return false;
    }

    /**
     * Inliner类实现了AST的重写
     */
    private class Inliner extends TreeTranslator {

        /**
         * 为了改变assert语句,我们这里重写了 visitAssert(JCAssert tree) 方法
         * @param tree
         */
        @Override
        public void visitAssert(JCAssert tree) {
            // 必须调用超类方法,以确保将转换也应用于节点的子代。
            super.visitAssert(tree);
            // 改写逻辑在makeIfThrowException这个方法中,结果赋值给 TreeTranslator.result
            result = makeIfThrowException(tree);
            tally++;
        }

        /**
         * 具体的assert语句转换逻辑:
         * assert cond : detail;
         * 转换为:
         * if (!cond) throw new AssertionError(detail);
         *
         * 该方法将一个断言语句作为参数，并返回一个if语句。
         * 这是一个有效的返回值，因为两个树节点都是语句，因此与Java语法等效。
         *
         * @param node
         * @return
         */
        private JCStatement makeIfThrowException(JCAssert node) {
            // make: if (!(condition) throw new AssertionError(detail);
            // 获取断言的 detail
            List<JCExpression> args = node.getDetail() == null
                    ? List.<JCExpression>nil()
                    : List.of(node.detail);
            // 创建了一个AST节点，该节点创建了“AssertionError”的新实例。
            JCExpression expr = make.NewClass(
                    null,
                    null,
                    // 使用Name.Table获取编译器内部字符串表示形式
                    make.Ident(names.fromString("AssertionError")),
                    args,
                    null);
            // 返回一个if语句
            return make.If(
                    // 倒置 assert的条件
                    make.Unary(JCTree.Tag.NOT, node.cond),
                    // 创建一个 throw 表达式
                    make.Throw(expr),
                    null);
        }
    }
}