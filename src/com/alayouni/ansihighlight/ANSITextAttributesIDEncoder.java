package com.alayouni.ansihighlight;

/**
 * Created by alayouni on 6/5/17.
 */
class ANSITextAttributesIDEncoder {

    public static final ANSITextAttributesIDEncoder UNSUPPORTED_CODE_ENCODER =
            new ANSITextAttributesIDEncoder(0, 0) {
                @Override
                int encode(int id) {
                    return id;
                }
            };

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
