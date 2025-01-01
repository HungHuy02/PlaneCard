package information;

import javacard.framework.Shareable;

public interface InformationInterface extends Shareable{
	
	void save(byte[] cardId, byte[] name, byte[] birthday, byte[] address, byte[] phoneNumber, byte[] modulus,
			byte[] exponent, byte[] image);
	void updateCardId(byte[] cardId);
	void updateName(byte[] name);
	void updateBirthday(byte[] birthday);
	void updateAddress(byte[] address);
	void updatePhoneNumber(byte[] phoneNumber);
	void updateModulus(byte[] modulus);
	void updateExponent(byte[] exponent);
	void updateImage(byte[] image);
	void updateTicketIds(byte[] ticketIds);
	byte[] getCardId();
	byte[] getName();
	byte[] getBirthday();
	byte[] getAddress();
	byte[] getPhoneNumber();
	byte[] getModulus();
	byte[] getExponent();
	byte[] getImage();
	byte[] getTicketIds();
	short getOneTicketIdLength();
}
