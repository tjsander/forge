package forge.game.spellability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * A plotted card can be cast from exile on a later turn, so the human's castable zone should list
 * it then - but not on the turn it was plotted.
 */
public class PlotZoneTest extends AITest {

    private Card plot(Game game, Player p, int plottedOnTurn) {
        Card aloe = addCardToZone("Aloe Alchemist", p, ZoneType.Exile);
        aloe.setPlotted(true);
        aloe.setTurnInZone(plottedOnTurn);
        return aloe;
    }

    @Test
    public void notListedOnTheTurnItWasPlotted() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, 3);
        Card aloe = plot(game, p, 3);
        game.getAction().checkStateEffects(true);

        assertFalse(p.getCardsIn(ZoneType.Flashback).contains(aloe));
    }

    @Test
    public void listedOnALaterTurn() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, 3);
        Card aloe = plot(game, p, 1);
        game.getAction().checkStateEffects(true);

        boolean engineOffersIt = false;
        for (SpellAbility sa : aloe.getAllPossibleAbilities(p, true)) {
            engineOffersIt |= sa.isAlternativeCost(AlternativeCost.Plotted);
        }
        assertTrue("the engine offers the plotted cast", engineOffersIt);
        assertTrue("so the castable zone lists it", p.getCardsIn(ZoneType.Flashback).contains(aloe));
    }

    /** No zone changes when the turn passes, so the view has to be refreshed when a turn begins. */
    @Test
    public void viewRefreshesWhenTheNextTurnBegins() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        int turn = game.getPhaseHandler().getTurn();
        Card aloe = plot(game, p, turn);
        p.updateFlashbackForView();
        assertFalse(p.getView().getFlashback().contains(aloe.getView()));

        playUntilNextTurn(game);

        assertTrue("a later turn has begun, so the plotted card is listed",
                p.getView().getFlashback().contains(aloe.getView()));
    }
}
