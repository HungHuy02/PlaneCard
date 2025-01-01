package information;

public class Person {
	private byte[] cardId;
	private byte[] name;
	private byte[] birthday;
	private byte[] address;
	private byte[] phoneNumber;
	private byte[] modulus;
	private byte[] exponent;
	private byte[] image;
	private byte[] ticketIds;
	
	public Person() {
		
	}
	
	public Person(byte[] cardId, byte[] name, byte[] birthday, byte[] address, byte[] phoneNumber, byte[] modulus,
			byte[] exponent, byte[] image) {
		super();
		this.cardId = cardId;
		this.name = name;
		this.birthday = birthday;
		this.address = address;
		this.phoneNumber = phoneNumber;
		this.modulus = modulus;
		this.exponent = exponent;
		this.image = image;
	}

	public byte[] getCardId() {
		return cardId;
	}

	public void setCardId(byte[] cardId) {
		this.cardId = cardId;
	}

	public byte[] getName() {
		return name;
	}

	public void setName(byte[] name) {
		this.name = name;
	}

	public byte[] getBirthday() {
		return birthday;
	}

	public void setBirthday(byte[] birthday) {
		this.birthday = birthday;
	}

	public byte[] getAddress() {
		return address;
	}

	public void setAddress(byte[] address) {
		this.address = address;
	}

	public byte[] getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(byte[] phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public byte[] getModulus() {
		return modulus;
	}

	public void setModulus(byte[] modulus) {
		this.modulus = modulus;
	}

	public byte[] getExponent() {
		return exponent;
	}

	public void setExponent(byte[] exponent) {
		this.exponent = exponent;
	}

	public byte[] getImage() {
		return image;
	}

	public void setImage(byte[] image) {
		this.image = image;
	}

	public byte[] getTicketIds() {
		return ticketIds;
	}

	public void setTicketIds(byte[] ticketIds) {
		this.ticketIds = ticketIds;
	}
	
}
