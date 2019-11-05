package top.anemone.joana.target.joana.slice;

import com.ibm.wala.cfg.exc.intra.MethodState;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarStreamModule;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.pruned.ApplicationLoaderPolicy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.ifc.sdg.mhpoptimization.CSDGPreprocessor;
import edu.kit.joana.wala.core.ExternalCallCheck;
import edu.kit.joana.wala.core.Main;
import edu.kit.joana.wala.core.SDGBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class SliceDemo {
    private static final Logger LOG = LoggerFactory.getLogger(SliceDemo.class);

    public static void main(String[] args) throws GraphIntegrity.UnsoundGraphException, CancelException, ClassHierarchyException, IOException {
        String jarFile="joana-target/target/joana-target-1.0-SNAPSHOT.jar";
        String[] libJars={};
        String exclusionsFile=null;
        String entryClass="Ltop/anemone/joana/target/Main";
        String entryMethod="main";
        String entryRef="([Ljava/lang/String;)V";
        JoanaLineSlicer.Line sink = new JoanaLineSlicer.Line("top/anemone/joana/target/Main.java", 17);
        String pdgFile="tmp.pdg";

        String result=computeSlice(jarFile, libJars, exclusionsFile, entryClass, entryMethod, entryRef, sink, pdgFile);
        System.out.println(result);
    }

    public static String computeSlice(String jarFile, String[] libJars, String exclusionsFile,
                                      String entryClass, String entryMethod, String entryRef,
                                      JoanaLineSlicer.Line sink, String pdgFile)
            throws ClassHierarchyException, IOException, GraphIntegrity.UnsoundGraphException, CancelException {
        SDG localSdg = null;

        LOG.info("Building SDG... ");
        String[] appJars={jarFile};
        // 产生SDG配置
        SDGBuilder.SDGBuilderConfig config = getSDGBuilderConfig(appJars, libJars, exclusionsFile);
        // 根据class, method, ref在classloader中找入口函数
        config.entry = findMethod(config, entryClass, entryMethod, entryRef);
        // 构造SDG
        localSdg = SDGBuilder.build(config);
        LOG.info("SDG build done! Optimizing...");
        // 剪枝
        CSDGPreprocessor.preprocessSDG(localSdg);
        LOG.info("Done! Saving to: " + pdgFile);
        // 保存SDG
        SDGSerializer.toPDGFormat(localSdg, new PrintWriter(pdgFile));
        LOG.info("Done!");

        LOG.info("Computing Slice...");
        // 利用SDG切片
        JoanaLineSlicer jSlicer = new JoanaLineSlicer(localSdg);
        LOG.info("Done...\n");
        // 根据sink点查找sinknodes
        HashSet<SDGNode> sinkNodes = jSlicer.getNodesAtLine(sink);
        Collection<SDGNode> slice = jSlicer.slice(sinkNodes);
        // 这里relatedNodesStr没搞懂是啥
        String relatedNodesStr = "[";
        for (SDGNode sdgNode : sinkNodes) {
            if (!jSlicer.toAbstract.contains(sdgNode) && !JoanaLineSlicer.isRemoveNode(sdgNode)) {
                relatedNodesStr += sdgNode.getId() + ", ";
            }
        }
        relatedNodesStr=relatedNodesStr.substring(0, relatedNodesStr.lastIndexOf(",")) + "]\n";
        String result = Formater.prepareSliceForEncoding(localSdg, slice, jSlicer.toAbstract);
        return result;
    }
	private static AnalysisScope makeMinimalScope(String[] appJars, String[] libJars,
                                                  String exclusionsFile, ClassLoader classLoader) throws IOException {

        String scopeFile = "RtScopeFile.txt";
		String exclusionFile = "Java60RegressionExclusions.txt";
		final AnalysisScope scope = AnalysisScopeReader.readJavaScope(
                scopeFile, (new FileProvider()).getFile(exclusionFile), classLoader);
		for (String appjar : appJars) {
			scope.addToScope(ClassLoaderReference.Application, new JarStreamModule(new FileInputStream(appjar)));
		}
		for (String lib: libJars) {
            scope.addToScope(ClassLoaderReference.Primordial, new JarStreamModule(new FileInputStream(lib)));
        }

		if (exclusionsFile != null) {
			List<String> exclusions = Files.readAllLines(Paths.get(exclusionsFile));
			for (String exc : exclusions) {
				scope.getExclusions().add(exc);
			}
		}
		return scope;
	}
    public static SDGBuilder.SDGBuilderConfig getSDGBuilderConfig(String[] appJars,
                                                                  String[] libJars, String exclusionsFile)
            throws ClassHierarchyException, IOException {
        SDGBuilder.SDGBuilderConfig scfg = new SDGBuilder.SDGBuilderConfig();
        scfg.out = System.out;

        scfg.nativeSpecClassLoader=new URLClassLoader(new URL[]{});
        scfg.scope = makeMinimalScope(appJars, libJars, exclusionsFile, scfg.nativeSpecClassLoader);
        scfg.cache = new AnalysisCacheImpl();
        scfg.cha = ClassHierarchyFactory.make(scfg.scope);
        scfg.ext = ExternalCallCheck.EMPTY;
        scfg.immutableNoOut = Main.IMMUTABLE_NO_OUT;
        scfg.immutableStubs = Main.IMMUTABLE_STUBS;
        scfg.ignoreStaticFields = Main.IGNORE_STATIC_FIELDS;
        scfg.exceptions = SDGBuilder.ExceptionAnalysis.IGNORE_ALL;
        scfg.pruneDDEdgesToDanglingExceptionNodes = true;
        scfg.defaultExceptionMethodState = MethodState.DEFAULT;
        scfg.accessPath = false;
        scfg.sideEffects = null;
        scfg.prunecg = 0;
        scfg.pruningPolicy = ApplicationLoaderPolicy.INSTANCE;
        scfg.pts = SDGBuilder.PointsToPrecision.INSTANCE_BASED;
        scfg.customCGBFactory = null;
        scfg.staticInitializers = SDGBuilder.StaticInitializationTreatment.SIMPLE;
        scfg.fieldPropagation = SDGBuilder.FieldPropagation.OBJ_GRAPH;
        scfg.computeInterference = false;
        scfg.computeAllocationSites = false;
        scfg.computeSummary = false;
        scfg.cgConsumer = null;
        scfg.additionalContextSelector = null;
        scfg.dynDisp = SDGBuilder.DynamicDispatchHandling.IGNORE;
        scfg.debugCallGraphDotOutput = false;
        scfg.debugManyGraphsDotOutput = false;
        scfg.debugAccessPath = false;
        scfg.debugStaticInitializers = false;
        return scfg;
    }

    static IMethod findMethod(SDGBuilder.SDGBuilderConfig scfg, final String entryClazz, final String entryMethod, String methodRef) {
        // debugPrint(scfg.cha);
        final IClass cl = scfg.cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application, entryClazz));
        if (cl == null) { throw new RuntimeException("Class not found: " + entryClazz); }
        // final IMethod m = cl.getMethod(Selector.make(entryMethod));
        final IMethod m = cl.getMethod(new Selector(Atom.findOrCreateUnicodeAtom(entryMethod),
                Descriptor.findOrCreateUTF8(Language.JAVA, methodRef)));
        if (m == null) { throw new RuntimeException("Method not found:" + cl + "." + entryMethod); }
        return m;
    }
}
