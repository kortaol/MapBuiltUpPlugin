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
                if (bl == Blocks.air) buildableTiles++;
                else if (bl.isPlaceable()) {buildableTiles++; occupiedTiles++; /*Log.info(bl.name);*/}

                /*
                Актуальная проблема -- подсчёт построек. Надо не считать валуны (они считаются derelict), размер построек должен учитываться,
                при этом почему-то не считаются постройки команды игрока, стоящие на карте изначально.

                Объяснение v2: функция подсчёта считает по тайлам, так что размер она учитывает автоматически. С другой стороны, по какой-то причине
                не учитываются фабрики, как на карте с озером, в то время как далее по коду оно считается удаляемым блоком.
                Мы используем разные функции для проверки, поэтому в данном случае будем учитывать по принципу "isPlaceable()"

                Этот подход сработал. При удалении внешних блоков остаются только ядро размером 25, как и было задумано.
                 */

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
            if (p < 2) Call.setHudText("Процент застройки: [orange]" + percent() + "%[].");
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
