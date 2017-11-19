package org.ctlv.proxmox.manager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.login.LoginException;

import org.ctlv.proxmox.api.Constants;
import org.ctlv.proxmox.api.ProxmoxAPI;
import org.ctlv.proxmox.api.data.LXC;
import org.ctlv.proxmox.api.data.Node;
import org.json.JSONException;

public class Manager {
	
	static Map<String, List<LXC>> myCTs = new HashMap<String, List<LXC>>();;
	static ProxmoxAPI api = new ProxmoxAPI();

	// Get the list of my CTs on a specific server
	private static List<LXC> getMyCTs(String server){
		List<LXC> myCTs = new ArrayList<LXC>();
		
		try{
			List<LXC> CTs = api.getCTs(server);
			for(LXC ct : CTs){
				if(ct.getName().startsWith(Constants.CT_BASE_NAME)){
					myCTs.add(ct);
				}
			}
		}catch( Exception e ){
			e.printStackTrace();
		}
		
		return myCTs;
	}
	
	// Move a CT from one server to another
	private static void migrate(String srvFrom, String srvTo) throws IOException, JSONException, LoginException {
		String ct = myCTs.get(srvFrom).get(0).getName();
		api.migrateCT(srvFrom, ct, srvTo);
	}
	
	// Stop the oldest CT on a specific server
	private static void stopOldCT(String srv){
		List<LXC> CTs = myCTs.get(srv);
		long ctTime = Long.MIN_VALUE;
		LXC oldestCT = null;
		
		// Find the oldest CT on the server
		for( LXC ct : CTs){
			if( ct.getUptime() > ctTime){
				oldestCT = ct;
				ctTime = ct.getUptime();
			}
		}
		
		// Stop the oldest CT
		try{
			if(oldestCT != null){
				api.stopCT(srv, oldestCT.getName());
			}
		}catch( Exception e){
			e.printStackTrace();
		}
	}
	
	// Get the total memory occupied by some CTs
	private static float getTotalMemory(List<LXC> CTs){
		float totalMem = 0;
		for( LXC ct : CTs ){
			totalMem += ct.getMem();
		}
		return totalMem;
	}
	
	public static void main(){
		
		while(true){
			
			// Update the lists of CTs
			myCTs.put(Constants.SERVER1, getMyCTs(Constants.SERVER1));
			myCTs.put(Constants.SERVER2, getMyCTs(Constants.SERVER2));

			long CTMemorySrv1, CTMemorySrv2;
			long allowedMemorySrv1, allowedMemorySrv2;
			long ratioSrv1, ratioSrv2;
			
			try {
				CTMemorySrv1 = (long) getTotalMemory(myCTs.get(Constants.SERVER1));
				CTMemorySrv2 = (long) getTotalMemory(myCTs.get(Constants.SERVER2));

				allowedMemorySrv1 = (long) (api.getNode(Constants.SERVER1).getMemory_total());
				allowedMemorySrv2 = (long) (api.getNode(Constants.SERVER2).getMemory_total());
				
				ratioSrv1 = CTMemorySrv1 / allowedMemorySrv1;
				ratioSrv2 = CTMemorySrv2 / allowedMemorySrv2;

				if(	   ratioSrv1 >= Constants.DROPPING_THRESHOLD
					&& ratioSrv2 >= Constants.DROPPING_THRESHOLD)
				{
					stopOldCT(Constants.SERVER1);
					stopOldCT(Constants.SERVER2);
				}
				else if( ratioSrv1 >= Constants.MIGRATION_THRESHOLD
					  && ratioSrv2 <= Constants.MIGRATION_THRESHOLD)
				{
					migrate(Constants.SERVER1, Constants.SERVER2);
				}
				else if( ratioSrv1 <= Constants.MIGRATION_THRESHOLD
					  && ratioSrv2 >= Constants.MIGRATION_THRESHOLD)
				{
					migrate(Constants.SERVER2, Constants.SERVER1);
				}
				else if( ratioSrv1 >= Constants.DROPPING_THRESHOLD)
				{
					migrate(Constants.SERVER1, Constants.SERVER2);
				}
				else if( ratioSrv2 >= Constants.DROPPING_THRESHOLD)
				{
					migrate(Constants.SERVER2, Constants.SERVER1);
				}
				
				Thread.sleep(Constants.MONITOR_PERIOD * 1000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
