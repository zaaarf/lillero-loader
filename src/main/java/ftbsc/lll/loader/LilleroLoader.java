package ftbsc.lll.loader;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;

import ftbsc.lll.IInjector;
import ftbsc.lll.exception.InjectionException;

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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public class LilleroLoader implements ILaunchPluginService {
	private static final Logger LOGGER = LogManager.getLogger(LilleroLoader.class.getCanonicalName());
	private static final Marker INIT     = MarkerManager.getMarker("INIT");
	private static final Marker RESOURCE = MarkerManager.getMarker("RESOURCE");
	private static final Marker HANDLER  = MarkerManager.getMarker("HANDLER");
	private static final Marker PATCHER  = MarkerManager.getMarker("PATCHER");

	public static final String NAME = "lll-loader";

	private List<IInjector> injectors = new ArrayList<>();

	public LilleroLoader() {
		LOGGER.info(INIT, "Patch Loader initialized");
	}

	@Override
	public String name() {
		return NAME;
	}


	// Load mods requesting patches from resources

	@Override
	public void offerResource(Path resource, String name) {
		LOGGER.warn(RESOURCE, "Resource offered to us ({}@{}) but no action was taken", name, resource.toString());
	}

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
				}
			} catch (MalformedURLException e) {
				LOGGER.error(RESOURCE, "Malformed URL for resource {} - 'file:{}'", row.getKey(), row.getValue().toString());
			}
		}
	}


	// Filter only classes we need to patch

	@Override
	public EnumSet<Phase> handlesClass(Type classType, final boolean isEmpty) {
		throw new IllegalStateException("Outdated ModLauncher"); //mixin does it
	}

	private static final EnumSet<Phase> YAY = EnumSet.of(Phase.BEFORE);
	private static final EnumSet<Phase> NAY = EnumSet.noneOf(Phase.class);

	@Override
	public EnumSet<Phase> handlesClass(Type classType, final boolean isEmpty, final String reason) {
		if (isEmpty) return NAY;
		// TODO can I make a set of target classes to make this faster
		LOGGER.debug(HANDLER, "Inspecting class {}", classType.getClassName());
		for (IInjector inj : this.injectors) {
			if (inj.targetClass().equals(classType.getClassName())) {
				LOGGER.info(HANDLER, "Marked class {} as handled by {}", classType.getClassName(), LilleroLoader.NAME);
				return YAY;
			}
		}
		return NAY;
	}


	// Process classes and inject methods

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
