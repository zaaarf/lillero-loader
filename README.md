# Lillero-loader
*"don't do coremods"*

Lillero-loader is a [ModLauncher](https://github.com/McModLauncher/modlauncher) plugin applying bare ASM patches to our beloved block game.

## Why
Up to Minecraft 1.12, patching the game's code wasn't such a complicated ordeal for a Forge modder: it was as simple as writing a "CoreMod" (even though in the more recent versions the Forge forums have been rather unkind to anyone dabbling in such things, from which comes our motto).

Forge would then load your mod when Minecraft itself was loading, allowing the developer to look up classes and patch them. Unfortunately, in 1.13+ the Forge team heavily reworked their code, making it impossible - as far as we can tell - to make CoreMods the same way we always did.

With `IFMLLoadingPlugin` gone, Forge and Mixin are now using [ModLauncher](https://github.com/McModLauncher/modlauncher) to modify the game's code. Refusing to give up our right to torture ourselves by writing bytecode, we set out to find a new way to do ASM patching.

We found it, and decided to share it with you, to spare you the pain of reading all that undocumented code.

## How
ModLauncher works by using [Java services](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html): mods seeking to use it, such as Forge, Mixin and ourselves, implement one of their service interfaces and register themselves as such.

Depending on what kind of service you registered as, your mod will be called at a specific time of loading. In our case, we are a `ILaunchPluginService`: we are loaded just before game classes are loaded in memory, and are fed their bytecode for patching.

This plugin only recognises patches written using `IInjector` from the [Lillero](https://git.fantabos.co/lillero) library. More details on the project's page.

### I know what I'm doing, tell me how the magic works
`LilleroLoader` implements the `ILaunchPluginService` interface and three of its methods. These methods are called in three phases*.
 * `addResources()`: will be called first for each external mod that was found, providing an absolute path to the JAR. `LilleroLoader` will try to load `IInjector` services from each of these JARs, saving them for later.
 * `handlesClass()`: will be called for each game class ModLauncher discovered. `LilleroLoader` will mark each of the classes the various `IInjector` asked for.
 * `processClassWithFlags()`: will be called for each marked game class, allowing to apply modifications to it.

*Technically, `handlesClass()` and `processClassWithFlags()` are called one after the other for each class loaded, but it's easier to understand if you disregard that.

# Installation
Right now the only way to include this loader in your Minecraft instance is to modify the launch profile adding it to the loaded classes.

### MultiMC / PolyMC / PrismLauncher
Edit your target instance and go into "Versions". Select "Forge", click "Customize" and then "Edit". A text editor should open on a json file. Inside the `libraries` array add the following objects:
```json
    {
        "downloads": {
            "artifact": {
                "sha1": "2af308a2026453c4bfe814b42bb71cb579c32a40",
                "size": 1502,
                "url": "https://maven.fantabos.co/ftbsc/lll/0.0.5/lll-0.0.5.jar"
            }
        },
        "name": "ftbsc:lll:0.0.5"
    },
    {
        "downloads": {
            "artifact": {
                "sha1": "fe23393f61060cacdc2a767ad82057006a923007",
                "size": 4568,
                "url": "https://maven.fantabos.co/ftbsc/lll/loader/0.0.7/loader-0.0.7.jar"
            }
        },
        "name": "ftbsc.lll:loader:0.0.7"
    },
```

### Vanilla launcher
Not documented yet, but should be possible by messing in a similar way with the version json files.