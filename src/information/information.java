package information;

import javacard.framework.*;

public class information extends Applet implements InformationInterface {
	private static Person person;
	private static final short ONE_TICKET_ID_LENGTH = (short) 4;

	public static void install(byte[] bArray, short bOffset, byte bLength) 
	{
		person = new Person();
		new information().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
	}

	public void process(APDU apdu)
	{
		if (selectingApplet())
		{
			return;
		}

		byte[] buf = apdu.getBuffer();
		switch (buf[ISO7816.OFFSET_INS])
		{
		case (byte)0x00:
			break;
		default:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}
	
	public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
		if(parameter != (byte)0x00)
			return null;
		return this;
	}
	
	public void save(byte[] cardId, byte[] name, byte[] birthday, byte[] address, byte[] phoneNumber, byte[] modulus,
			byte[] exponent, byte[] image) {
		person = new Person(cardId, name, birthday, address, phoneNumber, modulus, exponent, image);
	}
	
	public void updateCardId(byte[] carId) {
		person.setCardId(carId);
	}

	public void updateName(byte[] name) {
		person.setName(name);
	}
	
	public void updateBirthday(byte[] birthday) {
		person.setBirthday(birthday);
	}
	
	public void updateAddress(byte[] address) {
		person.setAddress(address);
	}
	
	public void updatePhoneNumber(byte[] phoneNumber) {
		person.setPhoneNumber(phoneNumber);
	}
	
	public void updateModulus(byte[] modulus) {
		person.setModulus(modulus);
	}
	
	public void updateExponent(byte[] exponent) {
		person.setExponent(exponent);
	}
	
	public void updateImage(byte[] image) {
		person.setImage(image);
	}
	
	public void updateTicketIds(byte[] ticketIds) {
		person.setTicketIds(ticketIds);
	}
	
	public byte[] getCardId() {
		return person.getCardId();
	}
	
	public byte[] getName() {
		return person.getName();
	}
	
	public byte[] getBirthday() {
		return person.getBirthday();
	}
	
	public byte[] getAddress() {
		return person.getAddress();
	}
	
	public byte[] getPhoneNumber() {
		return person.getPhoneNumber();
	}
	
	public byte[] getModulus() {
		return person.getModulus();
	}
	
	public byte[] getExponent() {
		return person.getExponent();
	}
	
	public byte[] getImage() {
		return person.getImage();
	}
	
	public byte[] getTicketIds() {
		return person.getTicketIds();
	}	
	
	public short getOneTicketIdLength() {
		return ONE_TICKET_ID_LENGTH;
	}
}
