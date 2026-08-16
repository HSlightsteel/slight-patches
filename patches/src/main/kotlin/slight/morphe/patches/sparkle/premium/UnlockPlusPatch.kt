package slight.morphe.patches.sparkle.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method

@Suppress("unused")
val unlockPlusPatch = bytecodePatch(
    name = "Unlock Plus",
    description = "Unlocks Sparkle TV's Plus features without purchase, including DVR recording, " +
        "timeshift, multiview, VOD (movies & series), multi-source setup, and custom channel/category editing.",
) {
    compatibleWith(
        Compatibility(
            name = "Sparkle TV",
            packageName = "se.hedekonsult.sparkle",
            appIconColor = 0x1A237E,
            targets = listOf(
                AppTarget("2.3.1"),
            ),
        ),
    )

    execute {
        // 1. Bypass the central capability gatekeeper: ph.b0.d(Context, int, int, String)Z.
        // Every UI and player gate checks whether (sync_internal & required_flag) == required_flag
        // via this static helper. Unconditionally returning true unlocks all gated features.
        val gatekeeperClass = classDefByStrings("notification_purchase_timeshift")
            .firstOrNull()
            ?: mutableClassDefByOrNull("Lph/b0;")
            ?: throw PatchException("Sparkle: gatekeeper class (ph.b0) not found.")
        val mutableGatekeeperClass = mutableClassDefBy(gatekeeperClass)

        val gateMethod = mutableGatekeeperClass.methods.firstOrNull { method: Method ->
            method.returnType == "Z" &&
                method.parameterTypes == listOf("Landroid/content/Context;", "I", "I", "Ljava/lang/String;") &&
                AccessFlags.STATIC.isSet(method.accessFlags)
        } ?: throw PatchException("Sparkle: gatekeeper method (Context, int, int, String)Z not found.")

        gateMethod.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        // 2. Force sync_internal distribution in MainActivity.H(int).
        // MainActivity receives the billing bitmask and propagates it to Intent extras, Fragment
        // bundles, and background broadcast services (TaskReceiver, EpgSyncService). Overriding
        // the parameter register p1 to 0xFF (255) ensures all components receive all feature flags
        // while remaining a valid positive integer (f24955s >= 0).
        // CRITICAL: Setting p1 to -1 (negative) broke the player engine (yh.q), because yh.q
        // explicitly gates video playback and state transitions behind `if (wVar.f24955s >= 0)`.
        // Passing -1 caused yh.q to enter an infinite 0ms spinning loop, freezing UI, crashing,
        // and completely preventing live channels from playing.
        val mainActivity = mutableClassDefByOrNull("Lse/hedekonsult/sparkle/MainActivity;")
            ?: throw PatchException("Sparkle: MainActivity not found.")

        val syncMethod = mainActivity.methods.firstOrNull { method: Method ->
            method.name == "H" &&
                method.returnType == "V" &&
                method.parameterTypes == listOf("I")
        } ?: throw PatchException("Sparkle: MainActivity.H(int) distribution method not found.")

        syncMethod.addInstructions(0, "const/16 p1, 0xff")

        // 3. Patch LibUtils native bridges (LibUtils.h, LibUtils.x, LibUtils.w, feature getters, and string constants).
        // - LibUtils.h: returns calculated bitmask from purchases; forced to 0xFF (255).
        // - LibUtils.x: seeds valid startup_time (0L) in SharedPreferences to prevent background anti-tamper sabotage.
        // - LibUtils.w: anti-debug watchdog check; forced to false (0).
        // - Feature bit & string getters: hooked in bytecode for full offline and JNI stability.
        val libUtilsClass = classDefByStrings("libutilsJNI")
            .firstOrNull()
            ?: mutableClassDefByOrNull("Lse/hedekonsult/utils/LibUtils;")
            ?: throw PatchException("Sparkle: LibUtils class not found.")
        val mutableLibUtils = mutableClassDefBy(libUtilsClass)

        // LibUtils.h(Context, ArrayList) -> int (force return 0xFF = 255)
        mutableLibUtils.methods.firstOrNull { method: Method ->
            method.name == "h" &&
                method.returnType == "I" &&
                AccessFlags.STATIC.isSet(method.accessFlags)
        }?.addInstructions(
            0,
            """
                const/16 v0, 0xff
                return v0
            """,
        )

        // LibUtils.x(MainActivity) -> void: Neutralize startup APK signature check.
        mutableLibUtils.methods.firstOrNull { method: Method ->
            method.name == "x" &&
                method.returnType == "V" &&
                AccessFlags.STATIC.isSet(method.accessFlags)
        }?.addInstructions(0, "return-void")

        // LibUtils.w() -> boolean (neutralize anti-debug check)
        mutableLibUtils.methods.firstOrNull { method: Method ->
            method.name == "w" &&
                method.returnType == "Z" &&
                method.parameterTypes.isEmpty() &&
                AccessFlags.STATIC.isSet(method.accessFlags)
        }?.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )

        // LibUtils.y(Context, String, String) -> boolean (purchase signature verify bypass)
        mutableLibUtils.methods.firstOrNull { method: Method ->
            method.name == "y" &&
                method.returnType == "Z" &&
                AccessFlags.STATIC.isSet(method.accessFlags)
        }?.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        // Feature bitmask getters in LibUtils for offline/JNI resilience:
        val featureFlags = mapOf(
            "f" to "0x1",  // Multi-source setup (1)
            "e" to "0x2",  // EPG & channel art cache (2)
            "c" to "0x4",  // Live TV preview session (4)
            "u" to "0x8",  // Timeshift / Catch-up (8)
            "a" to "0x10", // DVR / Recordings (16)
            "v" to "0x20", // VOD / Movies & Series (32)
            "g" to "0x40", // Multiview (64)
            "b" to "0x80", // Category / Channel Editing (128)
        )

        for ((methodName, flagHex) in featureFlags) {
            mutableLibUtils.methods.firstOrNull { method: Method ->
                method.name == methodName &&
                    method.returnType == "I" &&
                    method.parameterTypes.isEmpty() &&
                    AccessFlags.STATIC.isSet(method.accessFlags)
            }?.addInstructions(
                0,
                """
                    const/16 v0, $flagHex
                    return v0
                """,
            )
        }

        // Product SKU and name string getters in LibUtils:
        val stringGetters = mapOf(
            "i" to "Plus Subscription",
            "j" to "Plus",
            "k" to "sparkle_contribute_support_1",
            "l" to "sparkle_contribute_support_2",
            "m" to "sparkle_contribute_support_3",
            "n" to "sparkle_plus",
            "o" to "sparkle_plus_connected",
            "r" to "sparkle_plus_subscription_month",
            "s" to "sparkle_plus_subscription_year",
            "t" to "Shared Plus",
        )

        for ((methodName, stringVal) in stringGetters) {
            mutableLibUtils.methods.firstOrNull { method: Method ->
                method.name == methodName &&
                    method.returnType == "Ljava/lang/String;" &&
                    method.parameterTypes.isEmpty() &&
                    AccessFlags.STATIC.isSet(method.accessFlags)
            }?.addInstructions(
                0,
                """
                    const-string v0, "$stringVal"
                    return-object v0
                """,
            )
        }

        // 4. Force sync_internal in SetupActivity fragments (Add Source, Edit Source, Add XMLTV EPG).
        // SetupActivity does not use ph.b0.d for its UI buttons; it tests (this.sync_internal & LibUtils.f()) == LibUtils.f()
        // directly against internal fields on its fragment instances. When Google Play Billing returns no purchases
        // on a sideloaded APK, these fields remain 0, causing "Add new source" and "Add XMLTV EPG" to be grayed out
        // with "(Purchase Sparkle TV Plus)".
        // We inject `this.sync_field = 0xFF` (255) at the start of their UI rendering methods (`q1()V` and `S1(...)V`).
        
        // SetupActivity$j: Sources setup overview fragment ("Add new source")
        val setupJClass = mutableClassDefByOrNull("Lse/hedekonsult/tvlibrary/core/ui/SetupActivity\$j;")
            ?: classDefByStrings("setup_sources")
                .firstOrNull()
                ?.let { mutableClassDefBy(it) }

        setupJClass?.methods?.firstOrNull { method: Method ->
            method.name == "q1" &&
                method.returnType == "V" &&
                method.parameterTypes.isEmpty()
        }?.let { method ->
            val intFieldName = setupJClass.fields.firstOrNull { it.type == "I" }?.name ?: "r0"
            method.addInstructions(
                0,
                """
                    const/16 v0, 0xff
                    iput v0, p0, ${setupJClass.type}->$intFieldName:I
                """,
            )
        }

        // SetupActivity$d: Source EPG settings fragment ("Add XMLTV EPG")
        val setupDClass = mutableClassDefByOrNull("Lse/hedekonsult/tvlibrary/core/ui/SetupActivity\$d;")
            ?: classDefByStrings("setup_input_settings_epg")
                .firstOrNull()
                ?.let { mutableClassDefBy(it) }

        setupDClass?.methods?.firstOrNull { method: Method ->
            method.name == "q1" &&
                method.returnType == "V" &&
                method.parameterTypes.isEmpty()
        }?.let { method ->
            val intFieldName = setupDClass.fields.firstOrNull { it.type == "I" }?.name ?: "q0"
            method.addInstructions(
                0,
                """
                    const/16 v0, 0xff
                    iput v0, p0, ${setupDClass.type}->$intFieldName:I
                """,
            )
        }

        // SetupActivity$a: Add Source type selection fragment
        val setupAClass = mutableClassDefByOrNull("Lse/hedekonsult/tvlibrary/core/ui/SetupActivity\$a;")
            ?: classDefByStrings("setup_source_add_description")
                .firstOrNull()
                ?.let { mutableClassDefBy(it) }

        setupAClass?.methods?.firstOrNull { method: Method ->
            method.name == "S1" &&
                method.returnType == "V" &&
                method.parameterTypes == listOf("Ljava/util/ArrayList;")
        }?.let { method ->
            val intFieldName = setupAClass.fields.firstOrNull { it.type == "I" }?.name ?: "q0"
            method.addInstructions(
                0,
                """
                    const/16 v0, 0xff
                    iput v0, p0, ${setupAClass.type}->$intFieldName:I
                """,
            )
        }
    }
}
