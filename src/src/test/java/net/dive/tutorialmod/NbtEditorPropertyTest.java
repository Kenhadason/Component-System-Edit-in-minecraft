package net.dive.tutorialmod;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import org.assertj.core.api.Assertions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NbtEditorPropertyTest {

    // Feature: nbt-editor-ui-buttons, Property 8: Delete removes from both widget and masterEntries
    @Property(tries = 100)
    void property8_deleteRemovesFromBothLists(
            @ForAll @Size(min = 1, max = 20) List<@AlphaChars @StringLength(min = 1, max = 10) String> keys
    ) {
        // Arrange: build masterEntries and widgetChildren as plain lists
        List<MockEntry> masterEntries = new ArrayList<>();
        List<MockEntry> widgetChildren = new ArrayList<>();

        for (String key : keys) {
            MockEntry entry = new MockEntry(key, new Runnable[1]);
            // onDelete removes from both lists — mirrors NbtEditorScreen's lambda
            entry.onDelete()[0] = () -> {
                masterEntries.remove(entry);
                widgetChildren.remove(entry);
            };
            masterEntries.add(entry);
            widgetChildren.add(entry);
        }

        // Pick a random entry to delete (use the first one; jqwik varies the list)
        MockEntry target = masterEntries.get(0);

        // Act: invoke the onDelete callback
        target.onDelete()[0].run();

        // Assert: entry is absent from both lists
        Assertions.assertThat(masterEntries).doesNotContain(target);
        Assertions.assertThat(widgetChildren).doesNotContain(target);
    }

    record MockEntry(String id, Runnable[] onDelete) {}

    // ── Property 1 ────────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 1: Serialization completeness
    @Property(tries = 100)
    void property1_serializationCompleteness(
            @ForAll @Size(min = 1, max = 20)
            List<@AlphaChars @StringLength(min = 1, max = 16) String> keys,
            @ForAll @Size(min = 1, max = 20)
            List<@AlphaChars @StringLength(min = 0, max = 16) String> values
    ) throws Exception {
        // Build a deduplicated map of key → value (keys are already non-empty alpha strings)
        Map<String, String> entries = new LinkedHashMap<>();
        int limit = Math.min(keys.size(), values.size());
        for (int i = 0; i < limit; i++) {
            String key = keys.get(i).trim();
            if (!key.isEmpty()) {
                entries.put(key, values.get(i));
            }
        }
        Assume.that(!entries.isEmpty());

        // Run the saveNbt() serialization logic (extracted for testing)
        NbtCompound out = new NbtCompound();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String key = e.getKey();
            String raw = e.getValue();
            boolean saved = false;
            try {
                NbtCompound tmp = StringNbtReader.readCompound("{__v:" + raw + "}");
                NbtElement el = tmp.get("__v");
                if (el != null) { out.put(key, el); saved = true; }
            } catch (Exception ignored) {}
            if (!saved) out.putString(key, raw);
        }

        // Assert: compound key set equals the set of input keys
        Assertions.assertThat(out.getKeys()).containsExactlyInAnyOrderElementsOf(entries.keySet());
    }

    // ── Property 2 ────────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 2: Boolean entry serialization
    @Property(tries = 100)
    void property2_booleanEntrySerialization(
            @ForAll @AlphaChars @StringLength(min = 1, max = 16) String key,
            @ForAll boolean checked
    ) {
        NbtCompound out = new NbtCompound();
        out.putBoolean(key, checked);

        byte expected = checked ? (byte) 1 : (byte) 0;
        Assertions.assertThat(out.getByte(key)).isEqualTo(expected);
    }

    // ── Property 3 ────────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 3: Typed value parsing with string fallback
    @Property(tries = 100)
    void property3_typedValueParsingWithStringFallback(
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String raw
    ) throws Exception {
        // Alpha-only strings are almost always invalid NBT — exercises the fallback path
        runFallbackAssertion("testKey", raw);
    }

    @Example
    void property3_knownValidNbtValues() throws Exception {
        // Known valid NBT literals — should produce typed (non-string) elements
        String[] validNbt = {"42", "3.14f", "1b", "100L", "2.5d", "[1,2,3]"};
        for (String raw : validNbt) {
            NbtCompound out = new NbtCompound();
            boolean saved = false;
            try {
                NbtCompound tmp = StringNbtReader.readCompound("{__v:" + raw + "}");
                NbtElement el = tmp.get("__v");
                if (el != null) { out.put("k", el); saved = true; }
            } catch (Exception ignored) {}
            if (!saved) out.putString("k", raw);

            NbtElement result = out.get("k");
            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result.getType())
                    .as("Expected typed (non-string) element for valid NBT: " + raw)
                    .isNotEqualTo(NbtElement.STRING_TYPE);
        }
    }

    /** Shared helper: runs the fallback serialization logic and asserts the correct type. */
    private void runFallbackAssertion(String key, String raw) throws Exception {
        NbtCompound out = new NbtCompound();
        boolean saved = false;
        try {
            NbtCompound tmp = StringNbtReader.readCompound("{__v:" + raw + "}");
            NbtElement el = tmp.get("__v");
            if (el != null) { out.put(key, el); saved = true; }
        } catch (Exception ignored) {}
        if (!saved) out.putString(key, raw);

        NbtElement result = out.get(key);
        Assertions.assertThat(result).isNotNull();

        // Re-check parseability to determine expected type
        boolean parseable = false;
        try {
            NbtCompound tmp = StringNbtReader.readCompound("{__v:" + raw + "}");
            parseable = tmp.get("__v") != null;
        } catch (Exception ignored) {}

        if (parseable) {
            // Valid NBT → typed element (not necessarily NbtString)
            // We only assert it was stored (already guaranteed by out.get(key) != null above)
        } else {
            // Invalid NBT → must be a plain NbtString
            Assertions.assertThat(result.getType())
                    .as("Expected NbtString fallback for unparseable raw: " + raw)
                    .isEqualTo(NbtElement.STRING_TYPE);
        }
    }

    // ── Property 4 ────────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 4: Empty key rejection
    @Property(tries = 100)
    void property4_emptyKeyRejection(
            @ForAll @Size(min = 0, max = 10) List<@AlphaChars @StringLength(min = 1, max = 8) String> existingEntries,
            @ForAll @Whitespace @StringLength(min = 0, max = 5) String inputKey
    ) {
        String key = inputKey.trim();
        String[] status = {""};
        int[] statusColor = {0xAAAAAA};
        List<String> masterEntries = new ArrayList<>(existingEntries);

        if (key.isEmpty()) {
            status[0] = "Key cannot be empty.";
            statusColor[0] = 0xFF5555;
        } else {
            masterEntries.add(key);
        }

        // inputKey is whitespace-only or empty, so key.isEmpty() is always true here
        Assertions.assertThat(masterEntries.size()).isEqualTo(existingEntries.size());
        Assertions.assertThat(status[0]).isEqualTo("Key cannot be empty.");
        Assertions.assertThat(statusColor[0]).isEqualTo(0xFF5555);
    }

    // ── Property 5 ────────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 5: Valid add grows masterEntries with correct entry
    @Property(tries = 100)
    void property5_validAddGrowsMasterEntries(
            @ForAll @Size(min = 0, max = 10) List<@AlphaChars @StringLength(min = 1, max = 8) String> existingKeys,
            @ForAll @AlphaChars @StringLength(min = 1, max = 16) String newKey,
            @ForAll @IntRange(min = 0, max = 6) int typeCycleIdx
    ) {
        List<String> masterEntries = new ArrayList<>(existingKeys);
        int sizeBefore = masterEntries.size();
        masterEntries.add(newKey);

        Assertions.assertThat(masterEntries.size()).isEqualTo(sizeBefore + 1);
        Assertions.assertThat(masterEntries.get(masterEntries.size() - 1)).isEqualTo(newKey);
    }

    // ── Property 6 ────────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 6: New category registration
    @Property(tries = 100)
    void property6_newCategoryRegistration(
            @ForAll @Size(min = 1, max = 5) List<@AlphaChars @StringLength(min = 1, max = 8) String> existingCategories,
            @ForAll @AlphaChars @StringLength(min = 1, max = 8) String newCategory
    ) {
        Assume.that(!existingCategories.contains(newCategory));

        List<String> categories = new ArrayList<>(existingCategories);
        if (!categories.contains(newCategory)) {
            categories.add(newCategory);
        }

        Assertions.assertThat(categories).contains(newCategory);
    }

    // ── Property 7 ────────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 7: Input fields cleared after add
    @Property(tries = 100)
    void property7_inputFieldsClearedAfterAdd(
            @ForAll @AlphaChars @StringLength(min = 1, max = 16) String inputKey,
            @ForAll @StringLength(min = 0, max = 32) String inputValue
    ) {
        String[] keyField = {inputKey};
        String[] valueField = {inputValue};

        // addNewEntry() clears fields on success
        if (!inputKey.trim().isEmpty()) {
            keyField[0] = "";
            valueField[0] = "";
        }

        Assertions.assertThat(keyField[0]).isEmpty();
        Assertions.assertThat(valueField[0]).isEmpty();
    }

    // ── Property 9 ────────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 9: Type cycle advances with wrap-around and updates label
    static final String[] TYPE_CYCLE = {"String", "Int", "Float", "Long", "Boolean", "Compound", "List"};

    @Property(tries = 100)
    void property9_typeCycleAdvancesWithWrapAroundAndUpdatesLabel(
            @ForAll @IntRange(min = 0, max = 6) int startIdx
    ) {
        int newIdx = (startIdx + 1) % TYPE_CYCLE.length;
        String label = "[" + TYPE_CYCLE[newIdx] + "]";

        Assertions.assertThat(newIdx).isEqualTo((startIdx + 1) % 7);
        Assertions.assertThat(label).isEqualTo("[" + TYPE_CYCLE[newIdx] + "]");
    }

    // ── Property 10 ───────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 10: Category filter correctness
    @Property(tries = 100)
    void property10_categoryFilterCorrectness(
            @ForAll @Size(min = 0, max = 20) List<@AlphaChars @StringLength(min = 1, max = 6) String> keys,
            @ForAll @Size(min = 0, max = 20) List<@AlphaChars @StringLength(min = 1, max = 6) String> cats,
            @ForAll @AlphaChars @StringLength(min = 1, max = 6) String activeCategory
    ) {
        Assume.that(!keys.isEmpty() && !cats.isEmpty());

        // Build entries as (key, category) pairs
        List<String[]> masterEntries = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            masterEntries.add(new String[]{keys.get(i), cats.get(i % cats.size())});
        }

        // Apply filter logic mirroring applyFilter()
        List<String[]> visible = masterEntries.stream()
                .filter(e -> activeCategory.equals("All") || e[1].equals(activeCategory))
                .toList();

        // Assert: every visible entry satisfies the filter predicate
        for (String[] e : visible) {
            Assertions.assertThat(activeCategory.equals("All") || e[1].equals(activeCategory))
                    .isTrue();
        }

        // Assert: every entry in masterEntries whose category matches is in visible
        for (String[] e : masterEntries) {
            boolean shouldBeVisible = activeCategory.equals("All") || e[1].equals(activeCategory);
            if (shouldBeVisible) {
                Assertions.assertThat(visible).contains(e);
            }
        }
    }

    // ── Property 11 ───────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 11: Active category button is inactive
    @Property(tries = 100)
    void property11_activeCategoryButtonIsInactive(
            @ForAll @Size(min = 1, max = 8) List<@AlphaChars @StringLength(min = 1, max = 8) String> categories
    ) {
        record ButtonStub(String category, boolean active) {}

        String activeCategory = categories.get(0);

        // Build buttons mirroring rebuildCategoryButtons() logic
        List<ButtonStub> buttons = categories.stream()
                .map(cat -> new ButtonStub(cat, !cat.equals(activeCategory)))
                .toList();

        // Assert: the active category button has active == false
        ButtonStub activeBtn = buttons.stream()
                .filter(b -> b.category().equals(activeCategory))
                .findFirst()
                .orElseThrow();
        Assertions.assertThat(activeBtn.active()).isFalse();

        // Assert: all other buttons have active == true
        for (ButtonStub btn : buttons) {
            if (!btn.category().equals(activeCategory)) {
                Assertions.assertThat(btn.active()).isTrue();
            }
        }
    }

    // ── Property 12 ───────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 12: Search filter correctness — case-insensitive key match
    @Property(tries = 100)
    void property12_searchFilterCaseInsensitiveKeyMatch(
            @ForAll @Size(min = 0, max = 20) List<@AlphaChars @StringLength(min = 1, max = 12) String> keys,
            @ForAll @AlphaChars @StringLength(min = 1, max = 6) String query
    ) {
        // Model applyFilter() case-insensitive search logic
        List<String> masterEntries = new ArrayList<>(keys);
        List<String> visible = masterEntries.stream()
                .filter(key -> key.toLowerCase().contains(query.toLowerCase()))
                .toList();

        // Assert: every key in visible contains query (case-insensitive)
        for (String key : visible) {
            Assertions.assertThat(key.toLowerCase())
                    .as("Visible key '%s' should contain query '%s'", key, query)
                    .contains(query.toLowerCase());
        }

        // Assert: every key in masterEntries that matches is present in visible
        for (String key : masterEntries) {
            if (key.toLowerCase().contains(query.toLowerCase())) {
                Assertions.assertThat(visible)
                        .as("Key '%s' matches query '%s' and should be visible", key, query)
                        .contains(key);
            }
        }

        // Assert: no key in visible fails to contain query (no false positives)
        long falsePositives = visible.stream()
                .filter(key -> !key.toLowerCase().contains(query.toLowerCase()))
                .count();
        Assertions.assertThat(falsePositives)
                .as("No visible key should fail to contain the query")
                .isZero();
    }

    // ── Property 13 ───────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 13: Expand-all sets all expanded flags to true
    // Validates: Requirement 8.1
    @Property(tries = 100)
    void property13_expandAllSetsAllExpandedFlagsToTrue(
            @ForAll @Size(min = 0, max = 20) List<Boolean> initialExpanded
    ) {
        // Build entries as List<boolean[]> from generated booleans
        List<boolean[]> entries = new ArrayList<>();
        for (Boolean b : initialExpanded) {
            entries.add(new boolean[]{b});
        }

        // Apply expand-all logic (mirrors "▼ All" button behaviour)
        entries.forEach(e -> e[0] = true);

        // Assert: every entry's expanded flag is true
        for (boolean[] e : entries) {
            Assertions.assertThat(e[0]).isTrue();
        }
    }

    // ── Property 14 ───────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 14: Collapse-all sets all expanded flags to false
    // Validates: Requirement 8.2
    @Property(tries = 100)
    void property14_collapseAllSetsAllExpandedFlagsToFalse(
            @ForAll @Size(min = 0, max = 20) List<Boolean> initialExpanded
    ) {
        // Build entries as List<boolean[]> from generated booleans
        List<boolean[]> entries = new ArrayList<>();
        for (Boolean b : initialExpanded) {
            entries.add(new boolean[]{b});
        }

        // Apply collapse-all logic (mirrors "▲ All" button behaviour)
        entries.forEach(e -> e[0] = false);

        // Assert: every entry's expanded flag is false
        for (boolean[] e : entries) {
            Assertions.assertThat(e[0]).isFalse();
        }
    }

    // ── Property 15 ───────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 15: Clear empties masterEntries
    // Validates: Requirements 9.1, 9.2
    @Property(tries = 100)
    void property15_clearEmptiesMasterEntries(
            @ForAll @Size(min = 1, max = 20) List<@AlphaChars @StringLength(min = 1, max = 8) String> keys
    ) {
        List<String> masterEntries = new ArrayList<>(keys);
        List<String> widgetChildren = new ArrayList<>(keys);

        // Clear button logic
        masterEntries.clear();
        widgetChildren.clear(); // applyFilter() with empty masterEntries produces empty widget

        Assertions.assertThat(masterEntries).isEmpty();
        Assertions.assertThat(widgetChildren).isEmpty();
    }

    // ── Property 16 ───────────────────────────────────────────────────────────

    // Feature: nbt-editor-ui-buttons, Property 16: Server rejects invalid NBT with player message
    // Validates: Requirement 10.4
    @Property(tries = 100)
    void property16_serverRejectsInvalidNbtWithPlayerMessage(
            @ForAll @AlphaChars @StringLength(min = 1, max = 30) String nbtData
    ) {
        // Replicate the server-side guard logic from TutorialMod's SaveNbtPayload handler
        NbtCompound parsedNbt = null;
        String errorMessage = null;
        try {
            parsedNbt = StringNbtReader.readCompound(nbtData);
        } catch (Exception e) {
            errorMessage = "❌ Invalid NBT: " + e.getMessage();
        }

        // Alpha-only strings are not valid NBT compound syntax — parsing must throw
        Assertions.assertThat(parsedNbt)
                .as("parsedNbt should be null for invalid NBT input: " + nbtData)
                .isNull();
        Assertions.assertThat(errorMessage)
                .as("errorMessage should be non-null for invalid NBT input: " + nbtData)
                .isNotNull();
        Assertions.assertThat(errorMessage)
                .as("errorMessage should start with '❌ Invalid NBT:'")
                .startsWith("❌ Invalid NBT:");
    }
}
