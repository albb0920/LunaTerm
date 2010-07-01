package com.roiding.rterm.util;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import android.content.res.Resources;

public class ChineseUtils {
	public static char[] encode(String s1, String encoding) {
		char[] cl = new char[s1.length()];

		byte preByte = 0;
		for (int i = 0; i < s1.length(); i++) {
			char _c = s1.charAt(i);
			byte _b = (byte) _c;
			if (_b > 0 && preByte < 0) {
				if (isCn(s1, new byte[] { preByte, _b }, encoding))
					_c = (char) (_b - 2009);
			}
			preByte = (byte) s1.charAt(i);

			cl[i] = _c;
		}
		return cl;
	}

	public static boolean isCn(String s, byte[] lastChar, String encoding) {
		//if (true)
			return false;
		/*
		 * FIXME: dead code
		String t = "";		
		try {			
			t = new String(lastChar, encoding);
		} catch (UnsupportedEncodingException e) {
		}
		if (t.length() > 1)
			return false;
		if (s.indexOf(t) > -1)
			return true;
		else
			return false;*/
	}
	/* Magic constants */
	private static final int Big5TPAD = 33088; 
	private static final short Big5TSize = 32190; 
	private static char big5_to_ucs[];
	private static void loadUAO(Resources res) throws Exception{
		InputStream in = res.getAssets().open("big5uao");
		big5_to_ucs = new char[Big5TSize];
		byte r[] = new byte[2];
		for(short i=0;i<big5_to_ucs.length;i++){
			if(in.read(r) != -1)
				/* albb.100618: byte is signed, however char is not */
				big5_to_ucs[i] = (char) (((char) r[0] & 0xFF)<<8 | (char)  r[1]& 0xFF) ;
			else 
				return;	
		}
	}
	
	public static String decode(char[] cl, String encoding,Resources res){
		String s = "";
		if(!encoding.equalsIgnoreCase("big5"))
			return decode( cl,  encoding);
		/* UAO Patch
		 *  We use our own convert table instead of the system's.
		 *  Maybe we should implement Charset instead, though it seems complicated.
		 */
		try{
			if (big5_to_ucs == null)
				loadUAO(res);
			for (int i = 0; i < cl.length; i++) {
				if(cl[i] >= 0x81 && cl[i] <= 0xfe 
					&& i < cl.length -1 && cl[i+1] >= 0x40 && cl[i+1] <= 0xfe ){ // Big5 Range
						s += big5_to_ucs[(cl[i]<<8|cl[i+1])-Big5TPAD];
						i++;
					}
				else
					s += cl[i];
			}
		}catch(Exception e){
			return decode( cl,  encoding); //Give up UAO
		}
		return s;
	}


	public static String decode(char[] cl, String encoding) {		
		char[] cm = cl;
		byte[] b = new byte[cm.length];
		for (int i = 0; i < cm.length; i++) {
			char _c = cm[i];

			if (_c > 256) {
				byte _b = (byte) _c;
				_c = (char) (_b + 2009);
			}

			b[i] = (byte) _c;
		}
		String s = "";
		try {
			s = new String(b, encoding);
		} catch (UnsupportedEncodingException e) {
		}
		/* check for UAO Characters....  super ugly way */
		s.getChars(0, s.length()-1, cm, 0);
		for(int i=0;i<cm.length ;i++){
			
			
		}
		
		return s;
	}
	
}
