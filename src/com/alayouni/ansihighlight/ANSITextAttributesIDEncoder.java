package com.alayouni.ansihighlight;

/**
 * Created by alayouni on 6/5/17.
 */
class ANSITextAttributesIDEncoder {
    private final int resetMask;
    private final int mask;

    ANSITextAttributesIDEncoder(int resetMask, int mask) {
        this.resetMask = resetMask;
        this.mask = mask;
    }

    int encode(int id) {
        return (id & resetMask) | mask;
    }
}
