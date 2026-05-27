package pt.unl.fct.di.tardis.babel.iot.demos;

public interface BabelDemo {
	
	public final static String PROTO_NAME = "BabelDemo";
	public final static short PROTO_ID = 666;
	
	public void execute() throws Exception;
	
}
