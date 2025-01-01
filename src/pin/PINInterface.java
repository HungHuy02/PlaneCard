package pin;

import javacard.framework.Shareable;
import javacard.framework.PIN;

public interface PINInterface extends PIN, Shareable{
	boolean check(byte[] pin, short offset, byte length);
    void reset();
    void resetAndUnblock();
    void update(byte[] pin, short offset, byte length);
    byte getTriesRemaining();
    boolean isValidated();
    short getPINLength();
}
