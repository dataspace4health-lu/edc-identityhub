package com.nttdata.dataspace.ih.manageparticipant;

public class ParticipantConstants {

    private ParticipantConstants() {
    }
    
    //Env vars
    public static final String PARTICIPANT_ID_KEY = "edc.participant.id";
    public static final String SIGN_PC_ALGO_KEY = "edc.participant.keysign.algo";
    public static final String SIGN_PC_CURVE_KEY = "edc.participant.keysign.curve";
    
    //Formats for participant properties
    public static final String PARTICIPANT_DID_FORMAT_STRING = "did:web:%s";
    public static final String PARTICIPANT_PRIVATE_KEY_ALIAS = "%s-alias";
    public static final String PARTICIPANT_PUBLIC_KEY_ALIAS_FORMAT = "%s#key";

    //Endpoints
    public static final String CREATE_PARTICIPANT_EP = "v1alpha/participants/";
    
    //Key generation parameters
    public static final String KEY_ALGO_STRING = "algorithm";
    public static final String KEY_CURVE_STRING = "curve";
    
    public static final String SIGN_SCHEME_EDDSA = "EdDSA";
    public static final String SIGN_SCHEME_ED25519 = "Ed25519";
    public static final String SIGN_SCHEME_EC = "EC";
    public static final String SIGN_SCHEME_SECP256R1 = "secp256r1";


}

