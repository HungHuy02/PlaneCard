package pin;

import javacard.framework.*;

public class pin extends Applet implements PINInterface {
		
    private static final byte MAX_PIN_TRIES = 5;
    private static final byte PIN_LENGTH = 6;
    
    private OwnerPIN pinCode;
	
	private pin() {
        pinCode = new OwnerPIN(MAX_PIN_TRIES, PIN_LENGTH);
         byte[] defaultPIN = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
		 pinCode.update(defaultPIN, (short) 0, (byte) defaultPIN.length);
        register();
    }

     
	public static void install(byte[] bArray, short bOffset, byte bLength) 
	{
		new pin().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
	}
	

	public void process(APDU apdu)
	{
		if (selectingApplet())
		{
			return;
		}
		
		byte[] buf = apdu.getBuffer();
		apdu.setIncomingAndReceive();
	}
	
	public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
		if(parameter != (byte)0x00)
			return null;
		return this;
	}
	
    public boolean check(byte[] pin, short offset, byte length) {
        return this.pinCode.check(pin, offset, length);
    }
    
    public void update(byte[] pin, short offset, byte length) {
        this.pinCode.update(pin, offset, length);
    }
    
    public byte getTriesRemaining() {
        return this.pinCode.getTriesRemaining();
    }
    
    public void reset() {
	    this.pinCode.reset();
    }
    
    public void resetAndUnblock() {
	    this.pinCode.resetAndUnblock();
    }
    
    public boolean isValidated() {
	    return this.pinCode.isValidated();
    }
    
    public short getPINLength() {
	    return PIN_LENGTH;
    }
}


// /select 777777777777

// verify /send 00010000 05 010203040506

// update  /send 00020000 05 010203040506

// reset   /send 00030000
// reset block   /send 00040000