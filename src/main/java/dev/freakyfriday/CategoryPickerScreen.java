package dev.freakyfriday;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Small, category-only selector. Rows are populated entirely from the
 * authenticated server's observed category snapshot. Commands are still
 * validated against the current server session and current living target.
 */
public final class CategoryPickerScreen extends Screen {
    private record Row(String id, String label, int memberCount) {}
    private final String target;
    private final List<Row> rows;
    private boolean selected;

    public CategoryPickerScreen(String target, String encoded) {
        super(Component.literal("Freaky Friday"));
        this.target = target;
        this.rows = new ArrayList<>();
        for (String entry : encoded.split("\n")) {
            String[] pieces = entry.split("\\|", -1);
            if (pieces.length != 3 || !pieces[0].matches("[a-z0-9_]+")) continue;
            try {
                int count = Integer.parseInt(pieces[2]);
                if (count > 0 && count <= 10000 && rows.size() < 12) {
                    rows.add(new Row(pieces[0], pieces[1], count));
                }
            } catch (NumberFormatException ignored) {
                // Malformed offers are ignored, never submitted as commands.
            }
        }
    }

    @Override
    protected void init() {
        int width = Math.min(260, this.width - 24);
        int left = (this.width - width) / 2;
        int top = Math.max(14, (this.height - (rows.size() + 3) * 24) / 2);
        Button heading = Button.builder(Component.literal("Freaky Friday · " + target),
            b -> {}).bounds(left, top, width, 20).build();
        heading.active = false;
        this.addRenderableWidget(heading);
        int y = top + 25;
        for (Row row : rows) {
            this.addRenderableWidget(Button.builder(
                Component.literal(row.label + " · " + row.memberCount + " values"),
                b -> choose(row.id))
                .bounds(left, y, width, 20).build());
            y += 24;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"),
            b -> onClose()).bounds(left, y + 4, width, 20).build());
    }

    private void choose(String categoryId) {
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            selected = true;
            minecraft.player.connection.sendCommand("freakyfriday category " + categoryId);
            minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (!selected && minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("freakyfriday cancel");
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
