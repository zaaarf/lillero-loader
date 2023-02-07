# lillero-loader
*don't do coremods*

A simple LaunchPlugin loading bare ASM patches into our beloved block game.

## Why
Until minecraft 1.12, modifying minecraft game code wasn't too esoteric: it was necessary to register as "CoreMod", but Forge would then load your mod when minecraft itself was loading, allowing to lookup classes and patch them.
Since 1.13+ `net.minecraft.launchwrapper.Launch` and `net.minecraftforge.fml.relauncher.IFMLLoadingPlugin` are no longer available, replaced by cpw's modlauncher. This makes a new approach necessary.

## How
Modlauncher loads some Java services at startup (using builtin [ServiceLoader](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html) mechanism), allowing to load arbitrary classes and modules when launching Minecraft.
More specifically, the `ILaunchPluginService` is loaded before game classes are put in memory, and is passed all game classes for patching. This allows us to create a custom Launch Plugin (this project) that will load and apply arbitrary patches at start time.
Patches must be defined themselves as services inside each mod, implementing `IInjector` (from [lll](https://git.fantabos.co/lillero)) They must define in which class they will patch, and which method, and then implement an `inject()` method which will be called with the correct Method Node.
Note that, as of now, Searge names must be used for methods and fields.

### I know what I'm doing, spill your beans
Basically, our `ILaunchPluginService` implements 3 methods which are called in 3 phases:
 * `addResources()` : will be called first for each external mod that was found, providing an absolute path to the jar. Our LaunchPlugin will try to load `IInjector` services from each of these resources, saving them for later.
 * `handlesClass()` : will be called for each game class modlauncher discovered. Our LaunchPlugin will signal that it wants to change classes for which an `IInjector` was registered.
 * `processClassWithFlags()` : will be called for each game class which was signaled before, allowing to apply modifications to it.

*Technically, `handlesClass()` and `processClassWithFlags()` are called concurrently, but assuming they don't make the explanation easier.*