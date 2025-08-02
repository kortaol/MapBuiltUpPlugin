package mbupl;

import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.mod.Plugin;
import mindustry.world.Block;

public class MapBuiltUpPlugin extends Plugin {

    private int buildableTiles = 0;
    private int occupiedTiles = 0;

    private boolean lastWaveTriggered = false;

    private float percent() {
        float p = 100.0f * occupiedTiles / buildableTiles;
        return (float) (Math.floor(p * 10.0) / 10.0);
    }

    @Override
    public void init() {

//        Vars.state.rules.winWave = 120;
        /*
        Идея была неплохой, но вот вариант получше: мы выигрышную волну ставим как текущую * 1.5 (и не менее 75) и её же и спавним
        */

        Events.on(EventType.WorldLoadEvent.class, event -> {
            buildableTiles = 0; occupiedTiles = 0; Vars.state.rules.winWave = 0; lastWaveTriggered = false;

            World map = Vars.world;
            map.tiles.eachTile( t -> {
                Block bl = t.block();
                if (bl == Blocks.air && !t.floor().isLiquid) buildableTiles++; // Проблема: на водных картах процента не достичь из-за размера океана. Временное решение: игнорировать жидкости, хотя мелководья это тоже по идее будет затрагивать
                else if (bl.isPlaceable()) {buildableTiles++; occupiedTiles++;} // Считал через метод isPlaceable(): для существующего блока это актуально, для сломанного пришлось использовать другие методы
            });
            Call.setHudText("Процент застройки: [orange]" + percent() + "%[].");
            Log.info("Начальный процент застройки карты: " + percent() + ": " + occupiedTiles + "/" + buildableTiles + ".");
        });

        Events.on(EventType.PlayEvent.class, event -> {
            if (!Vars.state.rules.waves) Log.err("Wrong gamemode or map: cannot spawn waves");
        });

        Events.on(EventType.PlayerJoin.class, event -> Call.setHudText(event.player.con, "Процент застройки: [orange]" + percent() + "%[]."));

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (lastWaveTriggered || event.team == Team.derelict) return; // Подход через isPlaceable не сработал, ибо инфа блока после его уничтожения не передаётся

            int size = event.tile.block().size; // Размер блока на карте. Поле size блока содержит информацию о длине стороны,
            size *= size; // Поэтому настоящий размер блока получается при возведении его в квадрат
            occupiedTiles += event.breaking ? -size : size;
//            Call.sendMessage("Broken: " + occupiedTiles);

            float p = percent();
            if (p < 75) Call.setHudText("Процент застройки: [orange]" + percent() + "%[]."); // SORRY! 2% was left for debug purposes, my bad :3
            else {
                Call.setHudText("[red]Процент застройки: " + percent() + "%.\nНАЧАЛО ПОСЛЕДНЕЙ ВОЛНЫ.");

                Vars.state.rules.winWave = (int) Math.ceil(Math.max(75, 1.5f * Vars.state.wave));
                Vars.state.wave = Vars.state.rules.winWave - 1;
                Vars.logic.runWave();
                lastWaveTriggered = true;
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
            int size = event.tile.block().size; size *= size;
//            Call.sendMessage("Destroy: " + occupiedTiles);
            occupiedTiles -= size;
            Call.setHudText("[red]Процент застройки: " + percent() + "%.\nНАЧАЛО ПОСЛЕДНЕЙ ВОЛНЫ.");
        });
    }
}
