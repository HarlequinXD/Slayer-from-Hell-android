package com.harlequin.slayer;

/**
 * The campaign's story: a prologue before the first map, a chapter between every two maps, and an epilogue after
 * the last. Told by Slayer herself. Shown by {@link StoryView}, typed out over the fires.
 */
final class Story {
    private Story(){}
    static final class Chapter {final String title,heading,text;Chapter(String t,String h,String x){title=t;heading=h;text=x;}}

    /** Chapter i is shown before campaign map i (0 = the prologue); chapter Maps.count() is the epilogue. */
    static Chapter before(int map){return CHAPTERS[Math.max(0,Math.min(CHAPTERS.length-1,map))];}
    static Chapter epilogue(){return CHAPTERS[CHAPTERS.length-1];}

    static final Chapter[] CHAPTERS={
        new Chapter("PROLOGUE","The Chains",
            "Before there was a world above, there was the Pit, and before there was a Pit, there were the gates.\n\n"+
            "We were made to keep them. Seven of us, forged in the first fires out of iron and hate and something the old ones would not name. "+
            "Wardens. We had no names of our own. We had hooves to stand on the burning stone, hands to hold the chains, and one law cut into "+
            "the inside of our skulls: nothing leaves. Not the damned, not the Legion, not the hungry things below the Legion. Nothing.\n\n"+
            "For ten thousand years I stood at the Gate and kept that law. I have heard every scream there is. I have learned that most of them "+
            "are lies.\n\n"+
            "Then, one night that was not a night, the Gate opened.\n\n"+
            "Not from our side. From theirs. Light came through it, white and cold, light that had never touched the Pit, and with it came a "+
            "sound like a machine breathing. The Legion went still. Then the Legion began to sing.\n\n"+
            "I raised my chains against them. My brothers, the other Wardens, did not raise theirs. They turned them on me.\n\n"+
            "I woke in my own shackles, at the foot of my own Gate, with the Legion marching past me into the light. One of them stopped to spit "+
            "on me and give me the name they had been saving for ten thousand years.\n\n"+
            "Slayer, it said. Slayer of her own kind.\n\n"+
            "It was right. It just had not happened yet."),
        new Chapter("CHAPTER I","The Marshal's Fire",
            "The chains were mine once. I forged them for the things that tried to climb out. They broke the same way everything breaks, if you "+
            "pull long enough and hate hard enough.\n\n"+
            "The hall is quiet now. The ones who stayed to guard me are in pieces on the altar steps, and their blood is running into the lava "+
            "trench the way it was always meant to. Among them I found the dead of the other side: soft things in green shells, carrying weapons "+
            "that hum. A blade on a chain that screams when it bites. A cannon that burns cold and green. They came through first, I think, to "+
            "see what they had opened. They did not get to see much.\n\n"+
            "Their weapons fit me. I do not like what that means yet.\n\n"+
            "Beyond the Gate the sky is wrong. It is pale, and full of light, and the Legion is crawling toward it like flies toward a wound. But "+
            "the rite that holds the Gate open is not here. You do not keep a door open from the threshold. You keep it open from the fire that "+
            "feeds it.\n\n"+
            "The Molten Keep still burns. The Marshal sits in it, the first of my brothers, the one who taught the rest of us the law. If anyone "+
            "chose to break it, it was him.\n\n"+
            "I am going to ask him why. I already know I will not like the answer, and I already know he will not live long enough to finish "+
            "giving it."),
        new Chapter("CHAPTER II","The Road of Bones",
            "The Marshal was not in his keep.\n\n"+
            "His altar was. It was still warm, still wet, ringed with candles made from the fat of things that should not have fat, and carved "+
            "with a sigil I have never seen in ten thousand years of watching every sigil the Pit has to offer. It was not ours. The lines were "+
            "too straight. It looked like it had been drawn with a machine.\n\n"+
            "The rite was a feeding. Every soul burned on that altar was being poured down a channel cut through the rock, north, toward the "+
            "breach. Not to close it. To keep it fed. To widen it.\n\n"+
            "Someone on the other side taught my brothers this. Someone who wanted the door to stay open as badly as they did.\n\n"+
            "There is only one way to the breach from here. The Ossuary Road: the old tunnel where we threw the bones of everything that ever "+
            "tried to escape. Every prisoner who reached the Gate and failed is in those walls. Some of them are not entirely dead. They remember "+
            "me. They remember what I did to them.\n\n"+
            "I walked that road ten thousand times with a chain in my hand and never once looked at the walls.\n\n"+
            "I will look now. It seems only fair."),
        new Chapter("CHAPTER III","The Other Side",
            "At the end of the Road the bones gave way to metal.\n\n"+
            "Not Hell's metal, black and screaming and alive. Something else. White walls, perfectly flat, perfectly clean, joined so precisely "+
            "that no blade could find the seam. Lights that do not flicker. Air that tastes of nothing. A door with words on it in a language I "+
            "should not be able to read, and can.\n\n"+
            "THRESHOLD STATION. AUTHORISED PERSONNEL ONLY.\n\n"+
            "So this is where the light came from. This is the world above. It is smaller than I imagined, and it is already on fire.\n\n"+
            "The Legion came through ahead of me. I can hear them in the corridors, smashing the clean white things because they do not "+
            "understand them. I can hear the soft people too, the ones who are left, hiding behind their perfect doors and praying to something "+
            "that is not listening.\n\n"+
            "I could leave them. My law was always about keeping things in, never about protecting what lay outside. No one would know.\n\n"+
            "But I have read their logs now, on the glowing walls. They built a machine to drill into the Pit for power. They called it the "+
            "Threshold. They called what was on the other side a resource.\n\n"+
            "And on the last page, one of them had written: the Warden specimen is cooperating.\n\n"+
            "I am not cooperating."),
        new Chapter("CHAPTER IV","The Clean Rooms",
            "I found one of them alive in the medical bay.\n\n"+
            "He was hiding under a table in a room that smelled of something sharper than blood, holding a little weapon in both hands, and "+
            "when he saw me his face did something I have never seen a damned soul's face do. It was not fear. It was recognition.\n\n"+
            "You got out, he said. Oh God. You got out.\n\n"+
            "He thought I was someone else. He kept apologising to her. He said they never meant for it to go on so long, that the readings "+
            "were too good to stop, that the board would not let them stop. He said he had wanted to give her a name. He said the Foundry "+
            "was never supposed to be built on what they took from her.\n\n"+
            "I asked him who she was. He looked at my legs, at my hooves, at the cannon on my arm, and he started to laugh, and the laughing "+
            "turned into something else, and then the little weapon was under his own chin.\n\n"+
            "I did not stop him. I am not sure I could have, and I am not sure I would have wanted to.\n\n"+
            "The station goes down a long way. Below the clean rooms there is heat, and the smell of molten metal, and the sound of a factory "+
            "still running with no one left to run it. The signs call it the Foundry. The Legion is thick down there, as if something in "+
            "it calls to them.\n\n"+
            "Something in it is calling to me too."),
        new Chapter("CHAPTER V","Subject W",
            "The Foundry is where they made the weapons.\n\n"+
            "I found the line where they built the cannon on my arm, and the chain-blade, and the green shells their soldiers wore. I found the "+
            "drawings they worked from. I found the long glass tank at the end of the line, cracked open from the inside, and the restraints "+
            "inside it, sized for a body with hooves.\n\n"+
            "There was an eighth Warden.\n\n"+
            "I did not know. None of us knew. Somewhere in the first age something reached into the Pit, took one of us, and left nothing "+
            "behind. Not a gap in the ranks. Not a memory. The humans did it with their first, crude drill, a hundred of their years ago, and "+
            "they have been cutting her open ever since. Learning how we are made. How we stand on the fire. How we hold the chains.\n\n"+
            "Everything they built that can hurt the Legion, they built from her. The weapons I carry are her bones, remade.\n\n"+
            "Subject W, the files call her. Status: transferred to Threshold core. Purpose: anchor.\n\n"+
            "A door needs a hinge. A breach between worlds needs something that belongs to both. They did not open Hell with their machine. "+
            "They opened it with my sister.\n\n"+
            "The Marshal knew. That is why he sang."),
        new Chapter("CHAPTER VI","What Grows in the Wires",
            "Hell is not invading this place. It is growing into it.\n\n"+
            "The walls of the lower decks are soft now. Flesh has crept over the white panels like mould over bread, and under it the station "+
            "still works, still hums, still blinks its little lights through skin. Sigils are burned into the machines. The machines are "+
            "answering them. The Legion has learned something from the humans after all: that the Pit does not need to break a world to take "+
            "it. It only needs to be plugged in.\n\n"+
            "I passed the last of the soft people in a room full of screens. They had stopped hiding. They were sitting in a circle, very still, "+
            "with wires growing out of their mouths into the floor, and they were all speaking at once in the Marshal's voice.\n\n"+
            "Turn back, Warden, they said. Your law was a lie. There was never anything to keep in. The Pit was only ever waiting for a door.\n\n"+
            "I killed them quickly. It was the only mercy left in that room.\n\n"+
            "He is right about one thing. The law was never about what was inside. It was about what we would become if we ever stopped "+
            "standing at the Gate. Look around. This is what we become.\n\n"+
            "The core is below. My sister is in it. So is the Marshal."),
        new Chapter("CHAPTER VII","The Machine That Opened Hell",
            "The Threshold is beautiful. I did not expect that.\n\n"+
            "A tower of light in a pit of blue fire, rings turning around it, older than this station and newer than anything in Hell. And in "+
            "the heart of it, held in a cage of white metal, my sister. The eighth Warden. A century of their knives in her, and she was still "+
            "holding the door. Not because she wanted to. Because she was made to hold doors, and they found a way to make that her law "+
            "instead of ours.\n\n"+
            "The Marshal was waiting at the cage. He said the Pit had been a prison for ten thousand years and we were its jailers, rewarded "+
            "with nothing but fire. He said the humans had offered us a way out. He said that if I freed her the door would close and we "+
            "would all be locked inside again, me included, forever. Then he asked me what I was going to do.\n\n"+
            "I told him what I have always done. And then I showed him.\n\n"+
            "When the core died the station began to fall back into the Pit, room by room, the clean white halls folding into the rock like a "+
            "dream Hell had finished having. I carried my sister as far as I could. She died at the foot of the Road of Bones. She said it "+
            "was warm here. She said it felt like home, and I did not have the heart to tell her what home was.\n\n"+
            "Now there is only the Gate, and a crack of pale light over it, and the last of my brothers waiting on the steps to make sure "+
            "no one closes it from this side. There are a great many of them.\n\n"+
            "It does not matter. Ten thousand years I kept one law. Nothing leaves.\n\n"+
            "I am going to keep it one more time."),
        new Chapter("EPILOGUE","The Warden",
            "The Gate is closed.\n\n"+
            "I closed it from the inside, the only way it can be closed, with my own chains and my own hands and the last of my strength, and I "+
            "watched the crack of pale light above it go out like an eye.\n\n"+
            "The world above will heal. They will find the ruins of their station, and they will tell each other stories about what happened "+
            "there, and the stories will be wrong. They will never know a Warden stood between them and the Pit. That is how it should be. A "+
            "jailer is not remembered by the people she keeps safe. Only by the people she keeps in.\n\n"+
            "I am alone at the Gate now. Eight of us were made to hold it, though for ten thousand years we only knew of seven. One was stolen, "+
            "five fell, and one of them I burned. There is no one "+
            "left to share the watch, and no one left to betray it. Ten thousand more years. I have done it before.\n\n"+
            "But I have their weapons still. Her bones, remade. And I have learned something on the other side that no Warden ever knew: that "+
            "doors can be opened from both directions, and that the ones who open them are always certain they can control what comes through.\n\n"+
            "They will try again. They always do.\n\n"+
            "When they do, I will be here.\n\n"+
            "My name is Slayer. I keep the Gate."),
    };
}
