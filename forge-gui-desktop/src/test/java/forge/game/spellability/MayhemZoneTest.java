package forge.game.spellability;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * Mayhem casts from the graveyard only if the card was discarded this turn, so the human's
 * castable-from-graveyard zone should list it only then.
 */
public class MayhemZoneTest extends AITest {

    @Test
    public void notListedUnlessDiscardedThisTurn() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCards("Mountain", 2, p);
        Card bolt = addCardToZone("Electro's Bolt", p, ZoneType.Graveyard);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);

        assertFalse("milled or otherwise put there, not discarded: Mayhem can't be used",
                p.getCardsIn(ZoneType.Flashback).contains(bolt));
    }

    @Test
    public void listedAfterBeingDiscarded() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCards("Mountain", 2, p);
        Card bolt = addCardToZone("Electro's Bolt", p, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card discarded = p.discard(bolt, null, false, AbilityKey.newMap());
        game.getAction().checkStateEffects(true);

        assertTrue("discarded this turn, so it can be cast with Mayhem",
                p.getCardsIn(ZoneType.Flashback).contains(game.getCardState(discarded)));
    }
}
