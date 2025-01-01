package main;

import javacard.framework.*;
import pin.PINInterface;
import information.InformationInterface;
import javacardx.apdu.ExtendedLength;
import javacard.security.*;
import javacardx.crypto.*;

public class main extends Applet implements ExtendedLength{
	
	private static final byte[] pinAID = {(byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x01, (byte) 0x01};
	private static final byte[] inforAID = {(byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x02, (byte) 0x01};
	private static final byte[] cipherAID = {(byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x03, (byte) 0x01};
	
	private static final byte INS_VERIFY_PIN = (byte) 0x01;
    private static final byte INS_UPDATE_PIN = (byte) 0x02;
    private static final byte INS_RESET_PIN = (byte) 0x03;
    private static final byte INS_UNBLOCK_PIN = (byte) 0x04;
    private static final byte INS_ENTER = (byte) 0x05;
    private static final byte INS_PRINT_LENGTH = (byte) 0x06;
    private static final byte INS_PRINT = (byte) 0x07;
    private static final byte INS_CHANGE = (byte) 0x08;
    private static final byte INS_ADD_TICKET = (byte) 0x09;
    private static final byte INS_REMOVE_TICKET = (byte) 0x0A;
    private static final byte INS_ALL_TICKETS = (byte) 0x0B;
    private static final byte INS_BLOCK_PIN = (byte) 0x0C;
    private static final byte INS_RSA_AUTH = (byte) 0x0D;
    
    private static final short SW_VERIFICATION_FAILED = 0x7700;
    private static final short SW_PIN_BLOCKED = 0x7701;
    private static final short SW_PIN_VALIDATED = 0x7702;
    private static final short SW_PIN_NOT_VALIDATED = 0x7703;
    private static final short SW_INVALID_PIN_LENGTH = 0x7704;
    
	private static final short MAX_SIZE = (short) 32767;
	private static final short MAX_LENGTH_SEND = (short) 8190;
	private static final short MAX_LENGTH_COPY = (short) 506;
	
	private static final byte ID_ID_CARD = (byte) 0x70;
	private static final byte ID_NAME = (byte) 0x71;
	private static final byte ID_BIRTHDAY = (byte) 0x72;
	private static final byte ID_ADDRESS = (byte) 0x73;
	private static final byte ID_PHONE = (byte) 0x74;
	private static final byte ID_IMAGE = (byte) 0x75;
	
	private static final byte[] defaultPIN = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};

	private MessageDigest sha;
	private Cipher cipher;
	private short keyLen;
	private static short AES_KEY_LENGTH = (short) (KeyBuilder.LENGTH_AES_128 / 8);
	private byte[] hashValue;
	private Signature rsaSig;
	
	private static final short HASH_LENGTH = (short) 20;
	
	private main() {
		sha = MessageDigest.getInstance(MessageDigest.ALG_SHA, true);
		cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, true);
		rsaSig = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
	}

	
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		new main().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
	}

	public void process(APDU apdu){
		if (selectingApplet()){
			return;
		}

		byte[] buf = apdu.getBuffer();
		short recvLen = apdu.setIncomingAndReceive();
		switch (buf[ISO7816.OFFSET_INS]) {
			case INS_VERIFY_PIN:
				verifyPIN(apdu, buf);
				break;
			case INS_UPDATE_PIN:
				updatePIN(apdu, buf);
				break;
			case INS_RESET_PIN:
				resetPIN(apdu, buf);
				break;
			case INS_UNBLOCK_PIN:
				unblockPIN(apdu, buf);
				break;
			case INS_ENTER:
				saveInfor(apdu, buf, recvLen);
				break;
			case INS_PRINT_LENGTH:
				printLength(apdu, buf);
				break;
			case INS_PRINT:
				printInfor(apdu, buf);
				break;
			case INS_CHANGE:
				changeInfor(apdu, buf, recvLen);
				break;
			case INS_ADD_TICKET:
				addTicket(apdu, buf);
				break;
			case INS_REMOVE_TICKET:
				removeTicket(apdu, buf);
				break;
			case INS_ALL_TICKETS:
				getAllTickets(apdu, buf);
				break;
			case INS_BLOCK_PIN:
				blockPIN(apdu, buf);
				break;
			case INS_RSA_AUTH:	
				rsaAuth(apdu, buf);
				break;
			default:
				ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}
	
	private void verifyPIN(APDU apdu, byte[] buf) {
		PINInterface sio = (PINInterface) getSIO(pinAID);      
		byte pinLength = buf[ISO7816.OFFSET_LC];
		
		if (pinLength != sio.getPINLength()) {
			ISOException.throwIt(SW_INVALID_PIN_LENGTH);
			return;
		}

		if (!sio.check(buf, ISO7816.OFFSET_CDATA, pinLength)) {
			short triesRemaining = sio.getTriesRemaining();
			if (triesRemaining == 0) {
				ISOException.throwIt(SW_PIN_BLOCKED);
			} else {
				buf[0] = (byte) triesRemaining;
				apdu.setOutgoingAndSend((short) 0, (short) 1);
				ISOException.throwIt(SW_VERIFICATION_FAILED);
			}
			return;
		}
		byte[] pin = new byte[pinLength];
		Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, pin, (short) 0, pinLength);
		hashSHA(pin);
		ISOException.throwIt(SW_PIN_VALIDATED);
	}
	
	private void updatePIN(APDU apdu, byte[] buf) {
		PINInterface sio = (PINInterface) getSIO(pinAID);
        if (!sio.isValidated()) {
        	ISOException.throwIt(SW_PIN_NOT_VALIDATED);
            return;
        }
        byte pinLength = buf[ISO7816.OFFSET_LC];

        if (pinLength != sio.getPINLength()) {
        	ISOException.throwIt(SW_INVALID_PIN_LENGTH);
            return;
        }

        sio.update(buf, ISO7816.OFFSET_CDATA, pinLength);
        initAES(Cipher.MODE_DECRYPT);
        InformationInterface inforSIO = (InformationInterface) getSIO(inforAID);	
        byte[] cardId = decryptAES(inforSIO.getCardId());
		byte[] name = decryptAES(inforSIO.getName());
		byte[] birthday = decryptAES(inforSIO.getBirthday());
		byte[] address = decryptAES(inforSIO.getAddress());
		byte[] phoneNumber = decryptAES(inforSIO.getPhoneNumber());
		byte[] image = decryptAES(inforSIO.getImage());
		byte[] modulus = decryptAES(inforSIO.getModulus());
		byte[] exponent = decryptAES(inforSIO.getExponent());
		byte[] ticketIds = inforSIO.getTicketIds();
		short allTicketIdsLen = (short) ticketIds.length;
		if(allTicketIdsLen > (short) 1) {
			ticketIds = decryptAES(ticketIds);
		}
        byte[] pin = new byte[pinLength];
		Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, pin, (short) 0, pinLength);
		hashSHA(pin);
		initAES(Cipher.MODE_ENCRYPT);
		inforSIO.updateCardId(encryptAES(cardId));
		inforSIO.updateName(encryptAES(name));
		inforSIO.updateBirthday(encryptAES(birthday));
		inforSIO.updateAddress(encryptAES(address));
		inforSIO.updatePhoneNumber(encryptAES(phoneNumber));
		inforSIO.updateImage(encryptImage(image));
		inforSIO.updateModulus(encryptAES(modulus));
		inforSIO.updateExponent(encryptAES(exponent));
		if(allTicketIdsLen > (short) 1) {
			inforSIO.updateTicketIds(encryptAES(ticketIds));
		}
        apdu.setOutgoingAndSend((short) 0, (short) 0);
    }
    

     private void resetPIN(APDU apdu, byte[] buf) {
     	PINInterface sio = (PINInterface) getSIO(pinAID);
        sio.reset();
        apdu.setOutgoingAndSend((short) 0, (short) 0); 
    }

    private void unblockPIN(APDU apdu, byte[] buf) {
		PINInterface sio = (PINInterface) getSIO(pinAID);
        sio.resetAndUnblock();
        apdu.setOutgoingAndSend((short) 0, (short) 0);
    }
    
    private void blockPIN(APDU apdu, byte[] buf) {
	    PINInterface sio = (PINInterface) getSIO(pinAID);
	    byte[] temp = new byte[] {0x2E, 0x2E, 0x2E, 0x2E, 0x2E, 0x2E};
	    Util.arrayCopy(temp, (short) 0, buf, (short) 0, (short) 6);
	    for(short i = (short) 1; i < (short) 6; i++) {
		    sio.check(buf, (short) 0, (byte) 0x06);
	    }
	    apdu.setOutgoingAndSend((short) 0, (short) 0);
    }
    
    private void changeInfor(APDU apdu, byte[] buf, short recvLen) {
    	checkPinValidated();
	    InformationInterface sio = (InformationInterface) getSIO(inforAID);
	    initAES(Cipher.MODE_ENCRYPT);
	    short dataLen = apdu.getIncomingLength();
		if(dataLen > MAX_SIZE) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}
		short dataOffset = apdu.getOffsetCdata();
		byte[] temp = new byte[dataLen];
		short pointer = 0;
		while(recvLen > 0) {
			Util.arrayCopy(buf, dataOffset, temp, pointer, recvLen);
			pointer += recvLen;
			recvLen = apdu.receiveBytes(dataOffset);
		}
		short offset = 0;
		short len = 0;
		if(temp[offset] != ID_IMAGE) {
			for(short i = 0; i < dataLen; i++) {
				if(temp[i] == (byte) '.' || i == (short) (dataLen - 1)) {
					if(i == (short) (dataLen) - 1) {
						i++;
					}
					len = (short) (i - offset - 1);
					byte[] data = new byte[len];
					Util.arrayCopy(temp, (short) (offset + 1), data, (short) 0, len);
					switch(temp[offset]) {
						case ID_NAME:
							sio.updateName(encryptAES(data));
							break;
						case ID_BIRTHDAY:
							sio.updateBirthday(encryptAES(data));
							break;
						case ID_ADDRESS:
							sio.updateAddress(encryptAES(data));
							break;
						case ID_PHONE:
							sio.updatePhoneNumber(encryptAES(data));
							break;			
						default:
							ISOException.throwIt(ISO7816.SW_DATA_INVALID);
					}
					offset = (short) (i + 1);
					if(i < dataLen && temp[offset] == ID_IMAGE) {
						offset++;
						len = (short) (dataLen - offset);
						data = new byte[len];
						short destOff = (short) 0;
						while(len > 0) {
							short copyLen = len > MAX_LENGTH_COPY ? MAX_LENGTH_COPY : len;
							Util.arrayCopy(temp, offset, data, destOff, copyLen);
							offset += MAX_LENGTH_COPY;
							destOff += MAX_LENGTH_COPY;
							len -= MAX_LENGTH_COPY;
						}
						sio.updateImage(data);
						break;
					}
				}
			}	
		}else {
			offset++;
			len = (short) (dataLen - offset);
			byte[] data = new byte[len];
			short destOff = (short) 0;
			while(len > 0) {
				short copyLen = len > MAX_LENGTH_COPY ? MAX_LENGTH_COPY : len;
				Util.arrayCopy(temp, offset, data, destOff, copyLen);
				offset += MAX_LENGTH_COPY;
				destOff += MAX_LENGTH_COPY;
				len -= MAX_LENGTH_COPY;
			}
			sio.updateImage(encryptImage(data));
		}
    }
    
    private void printLength(APDU apdu, byte[] buf) {
    	checkPinValidated();
	    InformationInterface sio = (InformationInterface) getSIO(inforAID);
	    byte[] cardId = decryptAES(sio.getCardId());
	    byte[] name = decryptAES(sio.getName());
	    byte[] birthday = decryptAES(sio.getBirthday());
	    byte[] address = decryptAES(sio.getAddress());
	    byte[] phone = decryptAES(sio.getPhoneNumber());
	    byte[] image = decryptAES(sio.getImage());
	    short cardIdLen = (short) cardId.length;
	    short nameLen = (short) name.length;
	    short birthdayLen = (short) birthday.length;
	    short addressLen = (short) address.length;
	    short phoneLen = (short) phone.length;
	    short imageLen = (short) image.length;
	    short len = (short) (cardIdLen + nameLen + birthdayLen + addressLen + phoneLen + imageLen + 5); 
	    byte[] send = new byte[2];
		send[0] = (byte) ((len >> 8) & 0xFF); 
		send[1] = (byte) (len & 0xFF);        

		apdu.setOutgoing();                      
		apdu.setOutgoingLength((short) send.length);
		apdu.sendBytesLong(send, (short) 0, (short) send.length);
		
    }
    
    private void printInfor(APDU apdu, byte[] buf) {
    	checkPinValidated();
    	initAES(Cipher.MODE_DECRYPT);
	    InformationInterface sio = (InformationInterface) getSIO(inforAID);	    
		short le = apdu.setOutgoing();
		short sendLen = (MAX_LENGTH_SEND > le) ? le: MAX_LENGTH_SEND;
		byte[] separate = new byte[] {(byte) 0x2E};
		byte p1 = buf[ISO7816.OFFSET_P1];
		apdu.setOutgoingLength(sendLen);
		if(p1 == (byte) 0x00) {
			byte[] cardId = sio.getCardId();
			byte[] name = sio.getName();
			byte[] birthday = sio.getBirthday();
			byte[] address = sio.getAddress();
			byte[] phoneNumber = sio.getPhoneNumber();
			byte[] image = sio.getImage();
			cardId = decryptAES(cardId);
			name = decryptAES(name);
			birthday = decryptAES(birthday);
			address = decryptAES(address);
			phoneNumber = decryptAES(phoneNumber);
			image = decryptAES(image);
			short cardIdLen = (short) cardId.length;
			short nameLen = (short) name.length;
			short birthdayLen = (short) birthday.length;
			short addressLen = (short) address.length;
			short phoneNumberLen = (short) phoneNumber.length;
			apdu.sendBytesLong(cardId, (short) (0), cardIdLen);
			apdu.sendBytesLong(separate, (short) 0, (short) 1);
			apdu.sendBytesLong(name, (short) (0), nameLen);
			apdu.sendBytesLong(separate, (short) 0, (short) 1);
			apdu.sendBytesLong(birthday, (short) (0), birthdayLen);
			apdu.sendBytesLong(separate, (short) 0, (short) 1);
			apdu.sendBytesLong(address, (short) 0, addressLen);
			apdu.sendBytesLong(separate, (short) 0, (short) 1);
			apdu.sendBytesLong(phoneNumber, (short) 0, phoneNumberLen);
			apdu.sendBytesLong(separate, (short) 0, (short) 1);
			short len = (short) image.length;
			byte[] send = new byte[2];
			send[0] = (byte) ((len >> 8) & 0xFF); 
			send[1] = (byte) (len & 0xFF);
			apdu.sendBytesLong(send, (short) 0, (short) 2);	
		}else {
			byte p2 = buf[ISO7816.OFFSET_P2];
			short offset = (short) (p2 * MAX_LENGTH_SEND);
			apdu.sendBytesLong(decryptAES(sio.getImage()), offset, sendLen);
		}
		
    }	
		
    private void saveInfor(APDU apdu, byte[] buf, short recvLen) {
	    InformationInterface sio = (InformationInterface) getSIO(inforAID);
	    hashSHA(defaultPIN);
	    initAES(Cipher.MODE_ENCRYPT);
	    short dataLen = apdu.getIncomingLength();
		if(dataLen > MAX_SIZE) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}
		short dataOffset = apdu.getOffsetCdata();
		byte[] temp = new byte[dataLen];
		short pointer = 0;
		short count = (short) 6;
		while(recvLen > 0) {
			Util.arrayCopy(buf, dataOffset, temp, pointer, recvLen);
			pointer += recvLen;
			recvLen = apdu.receiveBytes(dataOffset);
		}
		short offset = 0;
		short len = 0;
		for(short i = 0; i < dataLen; i++) {
			if(temp[i] == (byte) '.') {
				len = (short) (i - offset);
				byte[] data = new byte[len];
				Util.arrayCopy(temp, offset, data, (short) 0, len);
				switch(count) {
					case (short) 6: 
						sio.updateCardId(encryptAES(data));
						break;
					case (short) 5:
						sio.updateName(encryptAES(data));
						break;
					case (short) 4:
						sio.updateBirthday(encryptAES(data));
						break;
					case (short) 3:
						sio.updateAddress(encryptAES(data));
						break;
					case (short) 2:
						sio.updatePhoneNumber(encryptAES(data));
						break;			
					default:
						ISOException.throwIt(ISO7816.SW_DATA_INVALID);
				}
				count--;
				offset = (short) (i + 1);
				if(count == 1) {
					break;
				}
			}	
		}
		len = (short) (dataLen - offset);
		byte[] data = new byte[len];
		short destOff = (short) 0;
		while(len > 0) {
			short copyLen = len > MAX_LENGTH_COPY ? MAX_LENGTH_COPY : len;
			Util.arrayCopy(temp, offset, data, destOff, copyLen);
			offset += MAX_LENGTH_COPY;
			destOff += MAX_LENGTH_COPY;
			len -= MAX_LENGTH_COPY;
		}
		sio.updateImage(encryptImage(data));
		byte[] ticketIds = new byte[] {0x01};
		sio.updateTicketIds(ticketIds);
		generateRSAKey(sio, apdu, buf);
    }
    
    private void addTicket(APDU apdu, byte[] buf) {
    	checkPinValidated();
	    InformationInterface sio = (InformationInterface) getSIO(inforAID);
	    byte[] ticketIds = sio.getTicketIds();
	    short oneTicketIdLength = sio.getOneTicketIdLength();
	    short length = (short) ticketIds.length;
	    byte[] newTicketIds;
	    if(length > (short) 1) {
		    initAES(Cipher.MODE_DECRYPT);
		    ticketIds = decryptAES(ticketIds);
		    newTicketIds = new byte[(short) (ticketIds.length + oneTicketIdLength)];
			Util.arrayCopy(ticketIds, (short) 0, newTicketIds, (short) 0, (short) ticketIds.length);
			Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, newTicketIds, (short) ticketIds.length, oneTicketIdLength);
	    }else {
		    newTicketIds = new byte[oneTicketIdLength];
		    Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, newTicketIds, (short) 0, oneTicketIdLength);
	    }
	    initAES(Cipher.MODE_ENCRYPT);
	    sio.updateTicketIds(encryptAES(newTicketIds));
    }
    
    private void removeTicket(APDU apdu, byte[] buf) {
    	checkPinValidated();
	    initAES(Cipher.MODE_DECRYPT);
	    InformationInterface sio = (InformationInterface) getSIO(inforAID);
	    byte[] ticketIds = decryptAES(sio.getTicketIds());
	    short oneTicketIdLength = sio.getOneTicketIdLength();
	    byte[] newTicketIds = new byte[(short) (ticketIds.length - oneTicketIdLength)];
	    for(short i = (short) 0; i < (short) ticketIds.length; i += oneTicketIdLength) {
	    	if(ticketIds[i] == buf[ISO7816.OFFSET_CDATA]) {
	    		boolean equal = true;
		    	for(short j = (short) 1; j < oneTicketIdLength; j++) {
					if(ticketIds[(short) (i + j)] != buf[(short) (ISO7816.OFFSET_CDATA + j)]) {
						equal = false;
						break;
					}
				}
				if(equal) {
					Util.arrayCopy(ticketIds, (short) 0, newTicketIds, (short) 0, (short) (i));
					if((short) (i + 4) < (short) ticketIds.length) {
						Util.arrayCopy(ticketIds, (short) (i + 4), newTicketIds, i, (short) (ticketIds.length - 4 - i));
					}
					break;
				}
	    	}
	    }
	    initAES(Cipher.MODE_ENCRYPT);
	    if((short) newTicketIds.length > (short) 0) {
		    sio.updateTicketIds(encryptAES(newTicketIds));
	    }else {
		    newTicketIds = new byte[] {0x01};
		    sio.updateTicketIds(newTicketIds);
	    }
	    
    }
    
    private void getAllTickets(APDU apdu, byte[] buf) {
    	checkPinValidated();
	    initAES(Cipher.MODE_DECRYPT);
	    InformationInterface sio = (InformationInterface) getSIO(inforAID);
	    byte[] ticketIds = sio.getTicketIds();
	    short length = (short) ticketIds.length;
	    if(length > (short) 1) {
	    	ticketIds = decryptAES(ticketIds);
		    Util.arrayCopy(ticketIds, (short) 0, buf, (short) 0, (short) ticketIds.length);
			apdu.setOutgoingAndSend((short) 0, (short) ticketIds.length);
	    }
    }
    
    private void checkPinValidated() {
	    PINInterface pinSIO = (PINInterface) getSIO(pinAID);
		if(!pinSIO.isValidated()) {
			ISOException.throwIt(SW_PIN_NOT_VALIDATED);
		}
    }
    
    private void rsaAuth(APDU apdu, byte[] buf) {
	    InformationInterface sio = (InformationInterface) getSIO(inforAID);
	    initAES(Cipher.MODE_DECRYPT);
	    byte[] cardId = decryptAES(sio.getCardId());
	    byte[] name = decryptAES(sio.getName());
	    byte[] address = decryptAES(sio.getAddress());
	    byte[] modulus = decryptAES(sio.getModulus());
	    byte[] exponent = decryptAES(sio.getExponent());
	    RSAPrivateKey privateKey = (RSAPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PRIVATE, KeyBuilder.LENGTH_RSA_1024, false);
	    privateKey.setModulus(modulus, (short) 0, (short) modulus.length);
	    privateKey.setExponent(exponent, (short) 0, (short) exponent.length);
	    rsaSig.init(privateKey, Signature.MODE_SIGN);
	    rsaSig.update(cardId, (short) 0, (short) cardId.length);
	    rsaSig.update(name, (short) 0, (short) name.length);
	    short len = rsaSig.sign(address, (short) 0, (short) address.length, buf, (short) 0);
	    apdu.setOutgoingAndSend((short) 0, len);
    }
    
    private void generateRSAKey(InformationInterface sio, APDU apdu, byte[] buf) {
	    KeyPair keyPair = new KeyPair(KeyPair.ALG_RSA,KeyBuilder.LENGTH_RSA_1024);
		keyPair.genKeyPair();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		byte[] temp = new byte[(short) 256];
		short len = privateKey.getModulus(temp, (short) 0);
		byte[] modulus = new byte[len];
		Util.arrayCopy(temp, (short) 0, modulus, (short) 0, len);
		sio.updateModulus(encryptAES(modulus));
		len = privateKey.getExponent(temp, (short) 0);
		byte[] exponent = new byte[len];
		Util.arrayCopy(temp, (short) 0, exponent, (short) 0, len);
		sio.updateExponent(encryptAES(exponent));
		short modulusLen = publicKey.getModulus(buf, (short) 0);
		buf[modulusLen] = (byte) 0x2E;
		short exponentLen = publicKey.getExponent(buf, (short) (modulusLen + 1));
		apdu.setOutgoingAndSend((short) 0, (short) (modulusLen + 1 + exponentLen));
    }
    
    private byte[] encryptImage(byte[] in) {
	    short check = (short) (in.length / 16);
		short fullBlockSize = (short) (16 * check);
		if(fullBlockSize < (short) in.length) {
			check++;
		}
		byte[] temp = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_DESELECT);
		byte[] data = new byte[(short) (check * 16)];
		short i;
		for( i = (short) 0; i < (short) (check - 1); i++) {
			Util.arrayCopy(in, (short) (i * 16), temp, (short) 0, (short) 16);
			Util.arrayCopy(encryptAES(temp), (short) 0, data, (short) (i * 16), (short) 16);
		}
		temp = JCSystem.makeTransientByteArray((short) (in.length - i * 16), JCSystem.CLEAR_ON_DESELECT);
		Util.arrayCopy(in, (short) (i * 16), temp, (short) 0, (short) (in.length - i * 16));
		Util.arrayCopy(encryptAES(temp), (short) 0, data, (short) (i * 16), (short) 16);
		return data;
    }
    
    private byte[] encryptAES(byte[] in) {
		byte[] paddedIn = paddingPKCS(in);
		short length = (short) paddedIn.length;
		byte[] out = new byte[length];
		cipher.doFinal(paddedIn, (short) 0, (short) paddedIn.length, out, (short) 0);
		return out;
	}
	
	private byte[] decryptAES(byte[] in) {
		short length = (short) in.length;
		byte[] out = new byte[length];
		cipher.doFinal(in, (short) 0, (short) length, out, (short) 0);
		byte[] removedPaddingOut = removePaddingPKCS(out);
		return removedPaddingOut;
	} 
     
    private byte[] paddingPKCS(byte[] in) {
		short check = (short) (in.length / 16);
		short fullBlockSize = (short) (16 * check);
		short lastBlockSize = (short) (in.length - fullBlockSize);
		byte padValue = (byte) (16 - lastBlockSize);
		if(padValue != (byte) 0x00 && padValue != (byte) 0x10) {
			byte[] out = JCSystem.makeTransientByteArray((short) (fullBlockSize + 16), JCSystem.CLEAR_ON_DESELECT);
			Util.arrayCopy(in, (short) 0, out, (short) 0, (short) in.length);
			for(short i = (short) (fullBlockSize + lastBlockSize); i < out.length; i = (short) (i + 1)) {
				out[i] = padValue;
			}
			return out;
		}
		return in;
	}
	
	private byte[] removePaddingPKCS(byte[] in) {
		byte lastByte = in[(short) (in.length - 1)];
		if(lastByte < (byte) 0x01 || lastByte > (byte) 0x0F) {
			return in; 
		}
		
		for(short i = (short) (in.length - 2); i > (short) (in.length - lastByte); i--) {
			if(in[i] != lastByte) {
				return in;
			}
		}
		
		byte[] out = JCSystem.makeTransientByteArray((short)(in.length - lastByte), JCSystem.CLEAR_ON_DESELECT);
		Util.arrayCopy(in, (short) 0, out, (short) 0, (short) out.length);
		return out;
	}
    
    private void initAES(byte mode) {
	    byte[] key = new byte[AES_KEY_LENGTH];
		Util.arrayCopy(hashValue, (short) 0, key, (short) 0, AES_KEY_LENGTH);
		AESKey aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES_TRANSIENT_RESET, KeyBuilder.LENGTH_AES_128, false);
		aesKey.setKey(key, (short) 0);
		cipher.init(aesKey, mode);
    }
    
    private void hashSHA(byte[] in) {
	    hashValue = new byte[HASH_LENGTH];
	    sha.doFinal(in, (short) 0, (short) in.length, hashValue, (short) 0);
    }
    
    private Shareable getSIO(byte[] appletID) {
	    AID aid = JCSystem.lookupAID( appletID, (short)0, (byte) appletID.length);
	    Shareable shareable = JCSystem.getAppletShareableInterfaceObject(aid, (byte)0x00);
	    if (shareable == null) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		} 
	    return shareable;
    }	
	
	private void sendResponse(APDU apdu, byte[] buf, short status) {
        Util.setShort(buf, (short) 0, status); 
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

}

// select
// /select 11223344550401
// nhap
// /send 000500000B012E012E012E012E012E01
// /send 00050000002E5143543035303132352E4E475559454E48554E474855592E32362F31312F323030322E89504E470D0A1A0A0000000D49484452000000960000009608060000003C0171E2000000C27A5458745261772070726F66696C6520747970652065786966000078DA6D50DB0DC33008FCF7141DC13CE2C0384E934ADDA0E3178C1DC54D4FE2C080CE403A3EEF577A381038F1B24AD152B2819515AB059203B531646EDCA0A5D760CEA7B3809622F3144FE97918793805C2558B968B903C7B619B0BCA5D5F7E84301CF9441EEF63A22E441805E80235D6CA4565BDAEB01D79868425279679ECDB7BB5EBED8BFD438807016563228901C88D13550BD41809BCD1DAAAB7361E2BD941FEDD69207D01FE9B5922571624DF00000184694343504943432070726F66696C650000789C7D913D48C3401CC55F53C52A55072B883864A84E765111C752C52258286D85561D4C2EFD82260D498A8BA3E05A70F063B1EAE0E2ACAB83AB20087E80383B3829BA4889FF4B0A2D623C38EEC7BB7B8FBB7780D0A830D5EC8A02AA6619A9784CCCE656C59E57F462180308202831534FA41733F01C5FF7F0F1F52EC2B3BCCFFD39FA95BCC9009F481C65BA61116F10CF6E5A3AE77DE2102B490AF139F1A44117247EE4BAECF21BE7A2C302CF0C1999D43C7188582C76B0DCC1AC64A8C433C46145D5285FC8BAAC70DEE2AC566AAC754FFEC2605E5B49739DE618E2584202498890514319155888D0AA91622245FB310FFFA8E34F924B2657198C1C0BA84285E4F8C1FFE077B766617ACA4D0AC680EE17DBFE18077A768166DDB6BF8F6DBB7902F89F812BADEDAF3680B94FD2EB6D2D7C040C6E0317D76D4DDE032E778091275D322447F2D3140A05E0FD8CBE29070CDD027D6B6E6FAD7D9C3E0019EA6AF906383804268A94BDEEF1EE40676FFF9E69F5F7035E0A729EBB6DE5DD00000D7669545874584D4C3A636F6D2E61646F62652E786D7000000000003C3F787061636B657420626567696E3D22EFBBBF222069643D2257354D304D7043656869487A7265537A4E54637A6B633964223F3E0A3C783A786D706D65746120786D6C6E733A783D2261646F62653A6E733A6D6574612F2220783A786D70746B3D22584D5020436F726520342E342E302D4578697632223E0A203C7264663A52444620786D6C6E733A7264663D22687474703A2F2F7777772E77332E6F72672F313939392F30322F32322D7264662D73796E7461782D6E7323223E0A20203C7264663A4465736372697074696F6E207264663A61626F75743D22220A20202020786D6C6E733A786D704D4D3D22687474703A2F2F6E732E61646F62652E636F6D2F7861702F312E302F6D6D2F220A20202020786D6C6E733A73744576743D22687474703A2F2F6E732E61646F62652E636F6D2F7861702F312E302F73547970652F5265736F757263654576656E7423220A20202020786D6C6E733A64633D22687474703A2F2F7075726C2E6F72672F64632F656C656D656E74732F312E312F220A20202020786D6C6E733A47494D503D22687474703A2F2F7777772E67696D702E6F72672F786D702F220A20202020786D6C6E733A746966663D22687474703A2F2F6E732E61646F62652E636F6D2F746966662F312E302F220A20202020786D6C6E733A786D703D22687474703A2F2F6E732E61646F62652E636F6D2F7861702F312E302F220A202020786D704D4D3A446F63756D656E7449443D2267696D703A646F6369643A67696D703A65643330643465362D366231332D346138612D386365622D353436353436363364386236220A202020786D704D4D3A496E7374616E636549443D22786D702E6969643A61653735313536332D396361302D343366342D386233352D393132623731326235613538220A202020786D704D4D3A4F726967696E616C446F63756D656E7449443D22786D702E6469643A31343033663138662D643335642D343164372D626131312D313939303465386463366633220A20202064633A466F726D61743D22696D6167652F706E67220A20202047494D503A4150493D22322E30220A20202047494D503A506C6174666F726D3D2257696E646F7773220A20202047494D503A54696D655374616D703D2231373236393237383135303631323937220A20202047494D503A56657273696F6E3D22322E31302E3338220A202020746966663A4F7269656E746174696F6E3D2231220A202020786D703A43726561746F72546F6F6C3D2247494D5020322E3130220A202020786D703A4D65746164617461446174653D22323032343A30393A32315432313A31303A31332B30373A3030220A202020786D703A4D6F64696679446174653D22323032343A30393A32315432313A31303A31332B30373A3030223E0A2020203C786D704D4D3A486973746F72793E0A202020203C7264663A5365713E0A20202020203C7264663A6C690A20202020202073744576743A616374696F6E3D227361766564220A20202020202073744576743A6368616E6765643D222F220A20202020202073744576743A696E7374616E636549443D22786D702E6969643A36383734396261312D656636612D346638392D616237312D366565343662386235623831220A20202020202073744576743A736F6674776172654167656E743D2247696D7020322E3130202857696E646F777329220A20202020202073744576743A7768656E3D22323032342D30392D32315432313A31303A3135222F3E0A202020203C2F7264663A5365713E0A2020203C2F786D704D4D3A486973746F72793E0A20203C2F7264663A4465736372697074696F6E3E0A203C2F7264663A5244463E0A3C2F783A786D706D6574613E0A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020202020200A2020202020202020202020202020202020202020202020202020200A3C3F787061636B657420656E643D2277223F3EE09F1B6900000006624B4744000000000000F943BB7F000000097048597300000B1300000B1301009A9C180000000774494D4507E809150E0A0F3CDA2E8200001DDC4944415478DAED7D5973E34A7AE5C94C6C24B8935A4BAA4D55A5BA7D6FF7C36DCF4474DB0F76D8BDCCCB4CCC3FF29F72F8CDCB8BFD64F7B87BAEAF6FED5A2889D4C29DC496801F804C812C6A07254AC217A16095441240E2E05B4E7E0B011020955412169A2E412A29B0524981954A0AAC54524981954A0AAC545260A5924A0AAC545260A592022B95541214255D825054450121040100728DCF8BCF719F83733F05560AA9509EAE3F81A22831885C0F5AFD411FF5BD460AAC1452A170CE438D15847BF2E2F532424808440202EEF374315360C5C0110308250494B14B818B520ACFF3A2F7926BE9BA14580F1D5C84C0B66D7CF7EDB7F89FFFE3CFE0BA9E04DB34D34729C3D1D111FEF19FFF198EE380921456695478165C82009AA6219FCF23C449301554845058B6856EAF770EF8528D95CA04B83CCFC3F6CE2E3CCF3B1738BE9F468029B02E6D1243B318FF39CB745EC5C94F4D612A3743652A29B04EBDA6E4CC682A8FC0145EC4A80711BD40084D049C2A53A0AACA99C4C36361E81F3CB02ECBA8737E7362931040D70DBC7CFEEC02F83D7C86FEC103EBB28CBA788FF8B9AAF90B82404688AEEB9E790CE07130F40F1E585761D429A550140594B24B832B0802288A22017C11C821A195FA580F2050BB1CA34E08E00701744DBB1C507C1FB55A15FFF7FFFC6FF87E70EEF13DCFC3DFFDFDDFA3DFEF83319646850F26EABB04A31E04A12EB16D07BD5EEF5CAD154400CC99B9E87DC105F16680C75474FEA808D2AB30EA04042038971CF53C0F3BBBBB179A4DE153795EA4058314580FCC245E8E51BF0A50E34EF979A690048F8B384D09D2545260A592022B951458A9A492022B91C83095342A4C185422820BB760CECAA7BA6AB47819B03E36402B8F07540114C630180EE1FB0154553DF7BD57C90C658C9D0DC610CD8F2E7D597934A05214348F8EF0C73FFE292C7CA0F42B9E9220DCD231741DD96CF6D29AA8DDE9C0F77D84BAF06B11DFFB98B4D6A302D6DEDE1EF6F6F6701602049BFE6A6303F97C1EAEEB5EA86928A5A8D7EB383E3E86A228E7824790B2C123A0DEE9C30755DCC721E14F30AE4FE22CBCA228C8E7F3A106BA005441108031865C2E37069CF07364EAFBC31F3CF814E607AFB19842A128EC4CA084FED469AE96AEEBC86432574A9BC99926582C1D27080230854DCF580D02504A81516A0AEFB57CDEDAC5057E35D69FAC42555570CE614E80E43C2184C0F77D98A6094551E4C6F6C81E61FFCB212825671EF3BC349B1458F7402E8AEE08215055559AA97C2E07C6D8A5FC2BA181344D43C630D0EDF540290501451004E0FCF17266730D2C4248580C4143BF2593D1913174F84110A5A20418F406F0031F7E4411F8BE7FA522054D5565FF05C6184CD3BC52F4260203339743BBD301630CBAA65DA9E6901002C62808A1A0948046AFF98229CD6900A0D71D80FB3E023F00F77D70CEE736D29C3B60E9BA8662A10043D7A0691A1485496253F84322A82304A8948A634E3A00D88E03CE3DB82E47A7DB81E772B89E37F578D9ECA93FA5AA2AB2D9ECA51CF7496098A629CF515555280A83EB4E3FA6C218544D45A95880C214689A1269CD537A2298A26D8BF9E2D8FF5DD785E7B9E8F6FBE8F787F03C9E026BEC2414866AA5829C990D4376DF07A29BE4381E824823610A17349E5F45414858F2A5290A0C1D2816F20000CFF3301A5968753A701C476AB57CCE948468369B85A669576E61E4FB3E72919F25C0109A534FBEC7D035944A45E8BA0E5DD3A499161C97EB7A61B07A21DF45645E19630C8C312C1A192C2D00B6EDA0D3EDA2DDE9DDB926BB5360A98A82A5A50598994C089C08009C7370CEC11883A669300C13D96C1619C380AAAA118800CE7D388E0DCB76E0D83686A391FCACD03AE287520AD3CC229F0F53894796856EB727F3CFE3D1DD65FDABB81F97C964A0691A46A3117CDF47B55CC6F1C909AA953232990C18631274A2ED91785828A5608C81520A5555A12A0A68049AF879789E07D775E1380E3CCF93DF4329959F5DA8D550AB5671DC6AA1D5EADC19C0EE045884102C2F2DA2903343DF2808C03987EBBA300C038B0B0B585A5A42AD5A45B55645219F970BEC3A2E2CDB82E3B870DDE82702A365DB180D87B06C1B966561341CA23F18C81B218E4D2985A1EBC82C1AF07D5FDE9C5C2E77ED1B21F8ACE1700800C8640D3CCBAD23F0436DEB7A5EA88923AE4CD775E44C13994C064626838C6140D33409A66919AE41CC8FB42C0B83C100FD7E1FED4E079665C9EB2084A056A9A05A2EE1F0E818ED4EEFE1032B9B31B0BAB21C9A9008509EE7A1582C62E3E54BBC7CF102D56A058661000046A3113A9D2EFAFD3E46D608AEEBC9C59DBC010A6328140A2846BFF37D1F9EE7C1B22CF47A3D74BA5DF47A3DD8B63DF6A44B07DC34AFEC5FC5B92933B60D4410B2F8424BE9BA8E423E8F62B1887C3E2FB59838FE644DE359F58D841049E2168BC5C85D70D0EDF570787888939393D0A98F7CB5C58505944A456C6FD7A555B815E5815B4CEDAF964BA8D52AD2BF715C17857C1EDFBCDDC4DBCD4D140A0500A1BFD1EB854F62BFDF97D18F781AC5029FA55DE2B9E87153E8FB7E7813BA5D1C9F9CA0DD6EC3711C04418062B188EFBEFBEE7ADD472360B65A2DFCE78F3F4ACDA2AA2A8AC522AA950A8AC5D0BF12E7210077D9BCF9F3AE33FE80F4FB7DECEFEFA37978784AC646DFBF77B08FC1C07A58C07ABEB60ACD30A2503904CA376F37F1CBEFBF47219F97E173BF3FC049AB857EBF0FDFF7A50F74D39641224D46802C0802D8B68DE3E363ECD6EBA8562A78F5EAD585D53BE79977D775F1EF7FF80328A5585E5A42AD5643369B95DA5368A159643A88EF0D7934A0D3EDE2F3972FE876BB720D1963386834D1E9F61E06B09EAF3D81A2A972F1B3D92CFEE2CF7F8D571B1BF0B8070480E338681E1EA1D3E98C69A759389FF1279D3106CBB2C03987611837066FBBD3412E9743C630A4669A1598CEBB36853170DFC7D6F636EAF5BAD45C8C51349A476877BAF71B58CFD756A14484A1E338A8562AF8CDDFFC356AB51A6CDB06630CED7607FB0707705D2F240A6FD13EC7CD45122016D1DF757CB5596830C6181A8D063E7CFA84203A27C628F61B4D74BBFDFB09AC6AB98C6AB52C1DCC855A0DFFEBF7BF432E9783EBBAA094A2D168E2F0E868CC44DD67B94DED74D9F3515515874747F8E9A79F2499AC280CDBBBBB188DEC991C776669333933835AB52C23B34AB98CDFFFEEB7304D53F2443BBBBB681E1ECED4ECDD0595326FE7E3BA2E166A35BC78F122D2A4618392B5D5D5999D2F9DD5C5AC2C2F8347262193C9E0B7BFF90DF2F9BC748E7776EB68B73B51EFAA54660D2ECFF3B050AB617171F174AB2800D65697EF0FB09E2C2F8DA9E23FFFF5AF50AB55E1380E18A3D8ADEFA1D3E95C9871994AC2379B522C2E2EA2502884410502640C0366363BFFC0D23415D95C78A28EE3E0EDE61BBC79F50A96658579E7CD23B45350DD99E6525515D55A4DEE89FA418095E5C5C44D62E2C05A5E5C08B92ACE512C14F0FDF7DFC3E31C0A63E8F5FA68340FC11E80937E5F455514E89A864AA53246BB948A85F90596A6AAC864C2AD18CFF3F08B9FFF1CE55229DC18F67D1C1C1CA4DDAAEF38420C3B1652E4F379B985E5FB3EAA9572A2F72651602D54CBF0FDB00755A552C1EBD7AF2457D53C3CC4C8B2246794CA1D9A4345010841A9543A6DA34929723973FE8045084126720239E7D878F902A6199EA8655968B7DA97CE254F65C6E65055812040369B85AEEBE15693EFA35C2ACE1FB00AF99CDC60D5751D2F5FBC04E71C94529CB45A703D3E33CE64323B20B847C5A1B77DDEC21C0A423A9FCF87C70560E806184B06128991482281CEE31CEBCBCB28974B329BA0D3E982B2641DF6C99DFD78FACCB49B352FC4653C9B61F2BCE3E73ECB3D46B147EA791E4CD3C4C9C989DC82D2540D236ECD07B008213062E9B6ABAB2B505515AEEBA2DBEDC175DD44E905F1D4094A63341AC1B6EDB0BF3A21501883AEEB30A2E4394551E0730E1E656CDE05C80448C4797B9E87E17008CBB2C2DC75CEC1A276E0D96C1646942D1BCF864DD26D1199B29AA641D7750C8743F8BE8F72B980D1FE9C004B551428AA02D7F5A0A92A161716C33C75DF47AFDF4B6CBB269E9C77D26AE1F0F0109D4E07AEEB7E95FC279E4AD334512A1651AD56653F86CBB4DA9E05A038E7383939C1F1C909BADD2E6CDBFE2A03425C5F269341B55AC5D2E222745D4F6472465C84BF4B08816118180C066164AFE9F3630A45254D1004300C03954A181D3A8E03CBB21389044539BBEBBAF8B2B58546A33196AF25801437279EE7A1D56AE1E4E404F5BD3D148B453C595D95CCF3AC33108466F53C0FFB0707383838407F30801FF99EA2143F9EDB2ECC60BFDF47B7DB45A3D1C0FADA1A969696129D8D18BF272201316C31AE82317663202702AC42B110E676FB3E0AF93CB4C82C0E876171039BD2D9E53A4F986559F8F1BFFE0BBD5E0F8AA2C81B4229890A0DBCE8BD8A3439F10286E6E1215AAD16969796B0BEBE0E5555AF9DD877B9074E41BBDDC6E72F5FD08B26B1524AC1A2164AA7E93A3E6C3B1CFDAB462946E2A1B16D1BEFDEBF87ED3878BABE9E18B8E21BFF9AA6C9C00B2050189D0F60299401D162140A05590635B246A1092464BCF0EF1AC239C7FB0F1FD0EBF5C62A970F8F8E311886357571C79831065551502E17918DF2CB49F454EEECEEA2D56E63636303E562115EC2668646F9FC9F3F7FC6DEFEFE579A95738EFE608876A70BCE450EFF6930A2AA0A16CA6564CDACBC96ADAD2DE89A86E5E5E5441E86F8281811259EFA6037B73089008BB253359EC964A0308691EBC2B61D79336F6A4E76EB75B45A2DD930CDF55C6CEFEC4DFD6E61063DCFC368DF022104E55211D5720988F6CB86C321FEF3871FB0B1B181858585C4020B42081CD7C5878F1F71747424996E4A295C97A3D16CA0D71F9CF979DF0FB5D7EE4103662683274F56E0FB3E28A5D8DED941A954BA72EDE3657C4061FEC27BA8C3B26F96A7459359CC48A523AC6426948247540349E046799E8746A311F9016105F0D676FD4A1D614E5A6D7CFCB20DCBB2A469F58300EF222D9854922121045BDBDB383C3C940F01A50CCDC3237CDEDA3A17549332188DB05BDF0FCBEE29C56834C2E1D151E244B300568000811FC86DB93B07168D4DD78A87D337E5614414D8EBF7311A8D22079DA0BE7F70ADEFF37D1F3BF57D340F8F4E2B78A2734DD2CF12C4B058874F9FBF5C3BC77C381A61301C4900B45BAD9944B57214CBCDBD966499F7533084279954C30A4AA9E4580821B06CF7CC9E0897956EAF1FE664DFA0F4EAC29B1489ED5837F6E14E4EDAD2D91E8E46893C0871967F92A49DD67EE9CE8115D70E4999162FE2A90821E09E77F38B168B18BDCC74132501D08A084D448B378DD8C4F74C0319904C51094D464B8527125636874F539050581C040188E47C82FB97763383BDBF24B49517554B4F3AF18945C6496927A10086C351A2C4631075310E7996B095E3636B6D6D9A19A955D488BFBB31B03C4F520EE2FF02B423CB9A1360C53684FBFD3E3CCF05634A224FA6EFFBC8E7F350A3BDC630DB31FF6840450841B55281EF471D07239EF026E64A9853F1808A0E3DE278C3A13D1FC0725D57DEF456BB0DD7F5A0282C11CD22AA7C2AD5AAF4DBAA95CAA99FF4C0A55A2E45116668AAAAD5EA8DF65E05CF26AD4C34D678DCE7F2E703589D6E4F9280C3E110ED761B5A42264B2CE2EACACAD893FAECE993876F02B306AA516D26E71CA552099572F9C674833D417E3A8E2349D8F8D6D85C682C3FD258B66DA3BEB7074D551323F238E7C8E57278F2E489F405545543B55A7EB0A0A2946275654576E6511405CF9E3EBDB1B6F23C2F24AE2360FABE8FD16874DA7F2CB23E7301ACB0B39E234F7E6B7B276AB2A127463970CEB1BEB6864AA5227394AAE5F283F4B7082178FE744D466A9C733C7DFA148542E1C6DA6A143568936631CA67135C56AF9F4C3F87C478AC4EA72BF38E9ACD26DA9DCEB51B999D493B1082571B1BC8643232ED6569710185048B00E6419E3F5D9384A8EBBA585A5AC293D5D51B814A682BCBB2C67A8C8976516217E22A5B4EB702AC10E9E19E96E338F8FF3FFC00339B4D740F8E738E4C2683CD376FC0227F8B731F2B2B4B30CDCC0301D513990EE4795ED8B76B6323917499FE6030F63D9EE7C9741E00B06C3BB1CECB89018B731F83E120F27F54BC7FFF01FDFE00866124D6A2502C76A150C0E6EBD7D2DFE0DCC7DAEA0A4CD3B8DFA05A7B024551E5759AA689D7AF5F83DE300397528A6194BE2DD68C31865EAF27FD2D4A29DA9D4E723E62920BD33C3C963BF122292F679AF0797284A958F45AAD8637AF5FC79E3E1F6BABABF7165CA2391D22CD9CCD66F1CDDBB7D0340DFC06EE84A013FAFDFE580DA1E33868B55A312ECB47B7D79F4F60B9AE8BC16004445989EF3F7E44ABDD86AE6B89A6D50ADF63717111AF363622DF4380EB090C43BF57A05A5F5B0D4105C08FCCFDCFBEF906994CE6C67E95EB79E874BB634D73092138393991EDA428A5383A394E74F729F14DE8FD461334DA72E09E279BBDCE2283C0755D2C2F2FE3C5F3E731707978B6BE76E36D8FDB925AA5824CD421DAF77D689A86B76FDF229BCD26032A31A43366020783013A9D8E4CED711D079D845B76270E2CCE398E8E8E6571C3F1F1313E7EFA34932A684943ACAFE3D9D3A7F24670CEF16CFDC9DCEF29964A05542A25B9A3C018C3E6E626F2A679A3D49838A8E2E0140468B3D98CB457580853DF6F24CFC3CD62C18E5B6D388E2B89BDFDFD7D349BCD99B52EE29CE3D9B36778BABE2EF7BC186378F16CFDEEC175C6F18BC53C96166A63A55F9B9B9B28178B7067002AE907379BD261678CE2F0F8048EEBDE0F6001407D6F3F6C3E115DECC74F9F6475CDACC0F5FCF973ACAEAC48769E523A75EBC70F825B6C9EFBF5EF726616CB8B0B925517A0AA562A330315A514C7C7C7320D1B08B7728E8F5B33B9EE9901CBF53CD40F0E24A3EB791E7E7AF70E23CB9A597310CE395EBE7C89855A4D9A1295A978BAB60A43D7A2EE7519AC2C2F440392C88C4115206B1828970AC8184634D92C8FD5E5257811A8C2062AE3E79C24A88489ED743A383E3E96BC22630C3BF5FD995DFB4C1B8076BB7DE4CD70C012A50CC3E110EFDEBDC3B73FFB99AC634BD25489311F9B9B9B707FF841B6A3D4340DCF9EAE9F6AAC89087516F03AADC86658A8D5BE2A48A584C08B4CF8F2F2F29507435D0554BD5E0FCD66330455E49ED4F7F613AFAEBE158D254DE27E03AEE30208CBB83A9D0E7E7AF74EA6D9245A6D128186528A37AF5FCBC84A5021727216F7647998EB7989CF9811F381C49EA698DAE5BADED8F92C2F2E8E051D37E1F5CE02D57038C4FEFEBE5C6746291ACD43F407C3D9BA96B8855EFD8410BC78B61EDA76417056AB78BBB92993F912D55C512D62B7D7C38F3FFE280B461963B2CE8FC5C6B6ADAEACC87DCD24AE756F7F1FED56E8BB7811B8E2C9749EE7A1542AE19BB76F6F4E16738E76BB3D15549665A15EAFCBBF514A7172D2C6D1C9C9EC6316DCD2100842085E3E7F7ACA9D4404E7E69B37D23CCD8AEB12CE6BFC353EC029A9C28FF871C50D9E9CE625B499611837F2352F03AABDBDBD31BFCDF35C7CD9AEDF4E308C5B9CFE4509C1F3487309BF606961016F660CAEB8269BA6DD6E8392182F612737D28E1781CAB66DD4EBF53B03D5ADF8589361FEF6EE1E180B7B3BA88A8246B38977EFDE496D9274B47851A7BCDB6C67146FAA362B50398EF395A672F9ED82EAD68125FC8B2FDB3BA7E052D59983EBA1C8654055AFD7C732445DCFC3D656FDF6CFF5364D615C745DC7B3F53570EE450D335C2C2D2E4AB39882EBFAA0927EACE7616B7BF76ECEF7AE807526B89696F0FAD5AB1449F7185477620AE362DB36B67676C7CD62A32137AD53391B54C26D984750DD39B0C6C1C524B80E0E0EF0F9CB97470FAEF34025A894BDBDBDB903D55C004B806B7BB72E3B0132C6B0B3B383ADEDED473B74E02250F9D10819DB3EEDF1EA713E17A09A1B60018065D9D88DC0150490ED110F0E0E646BC81454A7D445A3D1903DC38090039C1750CD15B0006030B250DFDD83A29C4E5DFFF8E9138E8F8F1FCD18BA8B404529C5D1D111BADD6E0C54013E6FEDCCD5FACCDDC4A4FE6884FDFD03E95FF94180F71F3E60381C3E78B3781EA8646F8C560BAD562B5A8BF0339FB7B6E76E5D1880BF9DB705B61D179C7398661604E1BEE270341A4B3FB9A9DCE68DB8CC395FA4A944A642A3D188F2D7015565D8A9D713E9B5F0A078AC8B647D751946262C44755D174FD7D7F1E2C58BA90971934099FCBBACFE0DFFF3D57BCEBAF944B4129FF6F7333251CFEA8E27FF1DFB5C10043237AB7D463AB178DFCECECE581DE041A3816E6F3097F76EAE8105002F9EAD9FD20E41806FBFFD16A5A8377B3C43E1A2214DA2249F47292CBE486789321B444A4B3CF9CD177D5445E3D7B1550B7FCFA25420A1558030E7891002CA1818A5F2953136369849742BF63847A7D3910FCC644604630CCD6613AD562B36F2A58DA3E393B9BD6F730F2C5551F0EAE5F33021CFF791334DFCE217BF908DF839E7B01D078E6DC3950975AE4CAEE39E072F4A55119BBF93E92CD35EA5A6403035855964AB4ED58A535EE3953262AA86A228505555A6ED88DFABAA2A9BFA2B8A827EBF8F7ABD3ED628EDF3D6CE7CFB8BF30E2C00C8E74DAC44B3647CDF47A552913E87E8ED246ECE599D80CFF3B382481B5EC70F1B3F86E8934A2EED5BC58F31A97D154581AEEBB02C2B6602093E7EDA024FB0007816A2CC3BA8C27B736A1E1863388932204F67D18C03203E8029343D5496FE534241E8A919628C41894C145314501A3AC6949EF6AC3F1B1590898401002FFA37E75C6AC9C0F7C3AAA0D80C42717E93D3CAE260132D8644BF85D3FE555E384562CE8135B71A8B108252311FB6858CA5D2485F89F328320A0732298A828C61209BCD229BCD2293C984330B751D9AAE41D33468AA064D53A3F7ABA074DC379BD47257D53893A636080270DF87EB386366DAB11DD88E83D16808CBB261DB3686C321469685D16824F3E485CF15F7CDC479D98E83A3A363395C2005D625249BCD60757949024A2C340018868162B1885AB58A42218F62B1887C2E0FD3CC4A1F45E6B3473C58100DC00CCEF0AF92A220A601711A70E3BF8B3F286222ED683442AFDF47BBDD46ABD5C6E1D111FABD3E6CC7963E9AF88ED1C8C2FE4123F141530F0E58D57209B56A45AA7AC77161183A565756F0FCF933ACAEAC209BCD42D334B068664FDCBFFA3A63949C518C4CC686924D1B5016FE2E3873E9C63F138CFD4E1CF3ACCF4F4EC598049D88FEB8E7C1765D74DA1DD4F7F6B0B5B585FD8370E44BB8D515BA07BB7BF5B0214B0AACAF657565296C7B1403CBE69BD7F8F977DFA15AADCA39C671209D67AEAEFBB7CBBCEFF2DA2D38B78BCB79DF333975550E02FDB2853FFEE94FD88FF65185EFB9BBB78FC18CCBBAEE1DB04A853C16166A726A42DE34F1EB5FFD0A1B1B2FA529140B7D5E58FF55C437955A90B1A0EC9F4E8818D3320E0A314770122C222088BF9F50723AB02AE68C9F92B30420906D193165D0F859AF22720DE71986033CFFDF7FFC07FEEDDFFF206FA5A62AF8F0F9CB5C30F173012C25E2AA449167B150C0EF7FF75B54AB551915C52782C6232CC951459158C86D455157E4B7F83232F3C1B9FF95B90C02B11097A71C0448A633F5A724571820501928301A0EE4945A88525016D5395216E3B84E1DF669D74E08097B907DF8807FF8C77F92C70F6B0AEE3ECB612EE886B041069724E15FFDD55F62717111C3E150FA598E63C3761CB88E0B27223EDD09B3F855350E19A736A79AB5E83D577DBA0204533F24C11A69BA499FFAACED9E69BB082159AA405535A8AA025D8BA2DB68BCAFEBBAF8E6ED26BADD1EFEE55FFF15BAAE43D33414F2B944BBF3DD4B60A9AA02D3CC80F3D0DCFDD92F7F895AB586DDDD3A2CDBC6281AA32634CFB4486BF2C64CDCC93B51C9E7EE3D4EF9FB24E044A468DB36020CA4198C13A78661843B113FFF0EF5BD3DECECEC40555594CBA51458957259FA39D96C16C54201EFDEBF0FFBC693D0678933D1977178EF835C749ED3B835F119D183A2DBEDE2901014F2793C595DC56EBD1E0DB5D2A0282CB10EC8D7913BCFC7CA44FD4239E728974A702362505114288C7DE5DC3EC634E5C9EB167E97E843219AFE67A3FEF74100E4EFB8F7FD9D028B310A3D1A9C4D2945A1509019028F1544D7D17A8410E8BA8E7C3E2F09E1DCE3061603624D57939C64F1D800460881699A92B1A5943D5E605172BA892C1CD2544B5D1F5CF10E36BAA62632DBF95E022B3EC449D33490B46FC3B523D02008A0AAAAFC77C89DD1C709AC6CC618CB924C4DE00D43FC280BE222CAE3C1032B98F0B75258DDCC144E6A2882470AACB11389E51BA572FD608810221FD847ABB1EED2B97CD8EA6B0E14457A17529989BF374F3E424A8ADE7C0D23E72A0556DCF114A550F1A2D2542EB97EF10DF8397838E7025894520C06037CFEFC792CA538952B3855A2ADF89CEC5C2877BC1C525B0D060374BBDD142337907806C85D7BF0770A2C0A3236A0F1C23ABE542EE763E1EC0CD747012CD7E3D03807F7FD343C9D8125989EAF7F4BDA13F7A0C43E95FB27A9A2482505562A29B0524981954A2A29B0524981954A0AAC54524981954A0AAC545260A5924A0AAC545260A592022B95541293FF069A41CA8167B77AC10000000049454E44AE426082
// lay ra so luong data can tra ve
// /send 00060000
// in
// it du lieu
// /send 000701000B
// nhieu du lieu
// /send 00070100001FFE
// 
// sa
// /send 000800000E71022E72022E73022E74022E7502

// /select 777777777777

// verify /send 00010000 05 010203040506

// update  /send 00020000 05 010203040506

// reset   /send 00030000
// reset block   /send 00040000