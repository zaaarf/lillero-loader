Lillero-loader
*"don't do coremods"*

Lillero-loader is a [ModLauncher](https://github.com/McModLauncher/modlauncher) plugin applying bare ASM patches to our beloved block game.

## Why
If one really wants to, patching the game's code is not that difficult: it was as simple as writing a "CoreMod". Forge would then load your mod when Minecraft itself was loading, allowing the developer to look up classes and patch them. This, however, is pretty inefficient, as it's applied at runtime. This system allows you to apply ASM patches on the same level as Mixin: before the game actually starts.

With `IFMLLoadingPlugin` gone, Forge and Mixin are now using [ModLauncher](https://github.com/McModLauncher/modlauncher) to modify the game's code. Refusing to give up our right to torture ourselves by writing bytecode, we set out to find a new way to do ASM patching.

We found it, and decided to share it with you, to spare you the pain of reading all that undocumented code.

## How
ModLauncher works by using [Java services](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html): mods seeking to use it, such as Forge, Mixin and ourselves, implement one of their service interfaces and register themselves as such.

Depending on what kind of service you registered as, your mod will be called at a specific time of loading. In our case, we are a `ILaunchPluginService`: we are loaded just before game classes are loaded in memory, and are fed their bytecode for patching.

This plugin only recognises patches written using `IInjector` from the [Lillero](https://github.com/zaaarf/lillero) library. More details on the project's page.

### I know what I'm doing, tell me how the magic works
`LilleroLoader` implements the `ILaunchPluginService` interface and three of its methods. These methods are called in three phases*.
 * `addResources()`: will be called first for each external mod that was found, providing an absolute path to the JAR. `LilleroLoader` will try to load `IInjector` services from each of these JARs, saving them for later.
 * `handlesClass()`: will be called for each game class ModLauncher discovered. `LilleroLoader` will mark each of the classes the various `IInjector` asked for.
 * `processClassWithFlags()`: will be called for each marked game class, allowing to apply modifications to it.

\*Technically, `handlesClass()` and `processClassWithFlags()` are called one after the other for each class loaded, but it's easier to understand if you disregard that.

## Installation
Right now the only way to include this loader in your Minecraft instance is to modify the launch profile adding it to the loaded classes. Eventually, we will write an installer similar to the one Forge uses to make the installation process painless.

### Vanilla launcher
Go to the version folder corresponding to the instance you want to install this on - generally the Forge one. You'll find them in your `.minecraft/versions` folder. In each of these folders there's a json file containing the list of libraries loaded on startup: we're going to edit it. The largest part is the `libraries` array. Add these two entries to it:
```json
{
	"downloads": {
		"artifact": {
			"sha1": "e76e307e791314de56f9b1f90fdffaf5992794c1",
			"size": 26762,
			"url": "https://maven.fantabos.co/ftbsc/lll/0.4.1/lll-0.4.1.jar"
		}
	},
	"name": "ftbsc:lll:0.4.1"
},
{
	"downloads": {
		"artifact": {
			"sha1": "71c8bd92b4aaab8f9b6bdd639e115b01d7060cb3",
			"size": 4667,
			"url": "https://maven.fantabos.co/ftbsc/lll/loader/0.1.3/loader-0.1.3.jar"
		}
	},
	"name": "ftbsc.lll:loader:0.1.3"
}
```

Whether you add them at the start or at the end does not particularly matter, and neither does indentation.

### MultiMC / PolyMC / PrismLauncher
Select and edit your target instance and go into the "Version" tab. Click "Add empty", put "Lillero" as name and "ftbsc.lll" as uid. A new library will appear. Select it, press "Edit", and your text editor of choice will open. Replace the contents with:

```json
{
	"formatVersion": 1,
	"libraries": [
		{
			"downloads": {
				"artifact": {
					"sha1": "e76e307e791314de56f9b1f90fdffaf5992794c1",
					"size": 26762,
					"url": "https://maven.fantabos.co/ftbsc/lll/0.4.1/lll-0.4.1.jar"
				}
			},
			"name": "ftbsc:lll:0.4.1"
		},
		{
			"downloads": {
				"artifact": {
					"sha1": "71c8bd92b4aaab8f9b6bdd639e115b01d7060cb3",
					"size": 4667,
					"url": "https://maven.fantabos.co/ftbsc/lll/loader/0.1.3/loader-0.1.3.jar"
				}
			},
			"name": "ftbsc.lll:loader:0.1.3"
		}
	],
	"name": "Lillero",
	"uid": "ftbsc.lll",
	"version": "0.4"
}
```
