package ftbsc.lll.loader;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;

import ftbsc.lll.IInjector;
import ftbsc.lll.exceptions.InjectionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements the {@link ILaunchPluginService} interface to create
 * a loader for Lillero patches ({@link IInjector}).
 */
public class LilleroLoader implements ILaunchPluginService {
	/**
	 * A Log4j logger instance.
	 */
	private static final Logger LOGGER = LogManager.getLogger(LilleroLoader.class.getCanonicalName());

	/**
	 * A Marker for the logger, used during the initialisation phase.
	 */
	private static final Marker INIT = MarkerManager.getMarker("INIT");

	/**
	 * A Marker for the logger, used during the resource processing phase.
	 */
	private static final Marker RESOURCE = MarkerManager.getMarker("RESOURCE");

	/**
	 * A Marker for the logger, used during class inspection.
	 */
	private static final Marker HANDLER = MarkerManager.getMarker("HANDLER");

	/**
	 * A Marker for the logger, used during the patching phase.
	 */
	private static final Marker PATCHER = MarkerManager.getMarker("PATCHER");

	/**
	 * The unique identifier assigned to this plugin.
	 */
	public static final String NAME = "lll-loader";

	/**
	 * A Set used to hold declared injectors.
	 */
	private final Set<IInjector> injectors = new HashSet<>();

	/**
	 * A Set used to hold the fully-qualified names of target classes.
	 * Used to improve performance.
	 */
	private final Set<String> targetClasses = new HashSet<>();

	/**
	 * THe default constructor.
	 */
	public LilleroLoader() {
		LOGGER.info(INIT, "Patch Loader initialized");
	}

	/**
	 * The unique name of this plugin, used by the launcher and other systems to find it.
	 * @return the name of the plugin
	 * @see ILaunchPluginService#name()
	 */
	@Override
	public String name() {
		return NAME;
	}

	/**
	 * Adds a resource to this plugin for processing by it.
	 * In our implementation, it does nothing but log that it happened.
	 * @param resource The resource to be considered by this plugin.
	 * @param name A name for this resource.
	 */
	@Override
	public void offerResource(Path resource, String name) {
		LOGGER.warn(RESOURCE, "Resource offered to us ({}@{}) but no action was taken", name, resource.toString());
	}

	/**
	 * Offer scan results from TransformationServices to this plugin.
	 * In practice, the paths of Forge mod JARs are offered to our processor here. In the method, we look for
	 * service provider files for {@link IInjector} and load them. We also memorise in a set the fully qualified
	 * name of classes that are to be patched.
	 * @param resources A collection of all the results
	 */
	@Override
	public void addResources(List<Map.Entry<String, Path>> resources) {
		LOGGER.debug(RESOURCE, "Resources being added:");
		for (Map.Entry<String, Path> row : resources) {
			LOGGER.debug(RESOURCE, "> {} ({})", row.getKey(), row.getValue().toString());
			try {
				URL jarUrl = new URL("file:" + row.getValue().toString());
				URLClassLoader loader = new URLClassLoader(new URL[] { jarUrl });
				for (IInjector inj : ServiceLoader.load(IInjector.class, loader)) {
					LOGGER.info(RESOURCE, "Registering injector {}", inj.name());
					this.injectors.add(inj);
					this.targetClasses.add(inj.targetClass());
				}
			} catch (MalformedURLException e) {
				LOGGER.error(RESOURCE, "Malformed URL for resource {} - 'file:{}'", row.getKey(), row.getValue().toString());
			}
		}
	}


	/**
	 * What is returned when the class being processed is to be patched.
	 */
	private static final EnumSet<Phase> YAY = EnumSet.of(Phase.BEFORE);

	/**
	 * What is returned when the class being processed is of no interest to any patch.
	 */
	private static final EnumSet<Phase> NAY = EnumSet.noneOf(Phase.class);

	/**
	 * Legacy method - only called by outdated ModLauncher versions. Since we don't really use
	 * the reason, however, we can support it.
	 * @param classType the class to consider
	 * @param isEmpty if the class is empty at present (indicates no backing file found)
	 * @return the set of {@link Phase}s the plugin wishes to be called back with
	 * @see LilleroLoader#handlesClass(Type, boolean, String)
	 */
	@Override
	public EnumSet<Phase> handlesClass(Type classType, final boolean isEmpty) {
		return handlesClass(classType, isEmpty, "unspecified");
	}

	/**
	 * Each class loaded is offered to the plugin for processing the plugin replies whether it's interested or not.
	 * The loader is only interested if the class's fully qualified name was earlier placed in the set.
	 * @param classType the class to consider
	 * @param isEmpty if the class is empty at present (indicates no backing file found)
	 * @param reason Reason for transformation request.
	 *               "classloading" - cpw.mods.modlauncher.api.ITransformerActivity#CLASSLOADING_REASON
	 *               "computing_frames" - cpw.mods.modlauncher.api.ITransformerActivity#COMPUTING_FRAMES_REASON
	 *               or the name of an {@link ILaunchPluginService}
	 * @return the set of {@link Phase}s the plugin wishes to be called back with
	 */
	@Override
	public EnumSet<Phase> handlesClass(Type classType, final boolean isEmpty, final String reason) {
		if (isEmpty) return NAY;
		LOGGER.debug(HANDLER, "Inspecting class {}", classType.getClassName());
		if(targetClasses.contains(classType.getClassName())) {
			LOGGER.info(HANDLER, "Marked class {} as handled by {}", classType.getClassName(), LilleroLoader.NAME);
			return YAY;
		}
		return NAY;
	}


	/**
	 * Each class loaded is offered to the plugin for processing.
	 * This is where the actual injection happens: the loader calls the {@link IInjector#inject(ClassNode, MethodNode)}
	 * method with the appropriate parameters, after finding them..
	 * @param phase The phase of the supplied class node
	 * @param classNode the classnode to process
	 * @param classType the name of the class
	 * @param reason Reason for transformation. "classloading" or the name of an {@link ILaunchPluginService}
	 * @return the {@link ComputeFlags} for this class
	 */
	@Override
	public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
		LOGGER.debug(PATCHER, "Processing class {} in phase {} of {}", classType.getClassName(), phase.name(), reason);
		List<IInjector> relevantInjectors = this.injectors.stream()
			.filter(i -> i.targetClass().equals(classType.getClassName()))
			.collect(Collectors.toList());
		boolean modified = false;
		for (MethodNode method : classNode.methods) {
			for (IInjector inj : relevantInjectors) {
				if (
					inj.methodName().equals(method.name) &&
					inj.methodDesc().equals(method.desc)
				) {
					LOGGER.info(PATCHER, "Patching {}.{} with {} ({})", classType.getClassName(), method.name, inj.name(), inj.reason());
					try {
						inj.inject(classNode, method);
						modified = true;
					} catch (InjectionException e) {
						LOGGER.error(PATCHER, "Error applying patch '{}' : {}", inj.name(), e.toString());
					}
				}
			}
		}

		return modified ? ComputeFlags.COMPUTE_FRAMES | ComputeFlags.COMPUTE_MAXS : ComputeFlags.NO_REWRITE;
	}
}
