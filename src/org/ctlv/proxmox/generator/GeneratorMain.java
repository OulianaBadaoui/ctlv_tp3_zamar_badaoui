package org.ctlv.proxmox.generator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.security.auth.login.LoginException;

import org.ctlv.proxmox.api.Constants;
import org.ctlv.proxmox.api.ProxmoxAPI;
import org.ctlv.proxmox.api.data.LXC;
import org.ctlv.proxmox.api.data.Node;
import org.json.JSONException;

public class GeneratorMain {
	
	static ProxmoxAPI api = new ProxmoxAPI();
	
	static Random rndTime = new Random(new Date().getTime());
	public static int getNextEventPeriodic(int period) {
		return period;
	}
	public static int getNextEventUniform(int max) {
		return rndTime.nextInt(max);
	}
	public static int getNextEventExponential(int inv_lambda) {
		float next = (float) (- Math.log(rndTime.nextFloat()) * inv_lambda);
		return (int)next;
	}
	
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
	
	public static void main(String[] args) throws InterruptedException, LoginException, JSONException, IOException {
		
	
		long baseID = Constants.CT_BASE_ID;
		int lambda = 50;
		
		Map<String, List<LXC>> myCTsPerServer = new HashMap<String, List<LXC>>();

		Random rndServer = new Random(new Date().getTime());
		Random rndRAM = new Random(new Date().getTime()); 
		
		long memAllowedOnServer1 = (long) (api.getNode(Constants.SERVER1).getMemory_total() * Constants.MAX_THRESHOLD);
		long memAllowedOnServer2 = (long) (api.getNode(Constants.SERVER2).getMemory_total() * Constants.MAX_THRESHOLD);
		System.out.println ("Max memory on server 7: " + memAllowedOnServer1); 
		System.out.println ("Max memory on server 8: " + memAllowedOnServer2); 
		
		List<LXC> CTs;
		
		// ARRET DES VM CREEES
		
		// Sur Srv-px8
		CTs = api.getCTs(Constants.SERVER1); 
		for (LXC ct: CTs) { 
			if ( ct.getName().contains("B15")) {
				try{ api.stopCT(Constants.SERVER1, ct.getVmid()); } catch (IOException e) { }
			}
		}
			
		// Sur Srv-px7
		CTs = api.getCTs(Constants.SERVER2);
		for (LXC ct: CTs) { 
			if ( ct.getName().contains("B15")) {
				try{ api.stopCT(Constants.SERVER2, ct.getVmid()); } catch (IOException e) { }
			}
		}

		while (true) {
			// Mise à jour des listes de nos CTs
			myCTsPerServer.put(Constants.SERVER1, getMyCTs(Constants.SERVER1));
			myCTsPerServer.put(Constants.SERVER2, getMyCTs(Constants.SERVER2));

			// Calcul de la quantite de RAM utilisee par mes CTs sur chaque serveur
			
			// Sur Srv-px7
			long memOnServer1 = 0;
			CTs = myCTsPerServer.get(Constants.SERVER1); 
			for (LXC ct: CTs) { 
				memOnServer1 += ct.getMem();
			}
			System.out.println("CT memory on server 7: " + memOnServer1);

			
			// Sur Srv-px8
			long memOnServer2 = 0;
			CTs = myCTsPerServer.get(Constants.SERVER2); 
			for (LXC ct: CTs) { 
				memOnServer2 += ct.getMem();
			}
			System.out.println("CT memory on server 8: " + memOnServer2);

			if (memOnServer1 < memAllowedOnServer1 && memOnServer2 < memAllowedOnServer2) {
				
				System.out.println("Ok there is still room for more CT ! (next CT: " + baseID + ")");
			
				// choisir un serveur aleatoirement avec les ratios specifies 66% vs 33%
				String serverName;
				if (rndServer.nextFloat() < Constants.CT_CREATION_RATIO_ON_SERVER1)
					serverName = Constants.SERVER1;
				else
					serverName = Constants.SERVER2;
				
				// creer un CT sur ce serveur
				try { 
					System.out.println("CreateCT(" + serverName + ", " + baseID + ", " + Constants.CT_BASE_NAME+baseID + ")");
					api.createCT(serverName, baseID+"", Constants.CT_BASE_NAME+baseID, 512000000);	
					Thread.sleep(1000);
					api.startCT(serverName, baseID+"");
					baseID++; 
				} catch (IOException e) { 
					e.printStackTrace();
				}

				// planifier la prochaine creation
				int timeToWait = getNextEventExponential(lambda); // par exemple une loi expo d'une moyenne de 30sec
				
				// attendre jusqu'au prochain evenement
				Thread.sleep(1000 * timeToWait);
			}
			else {
				System.out.println("Servers are loaded, waiting 5 more seconds ...");
				Thread.sleep(Constants.GENERATION_WAIT_TIME* 1000);
			}
		}
		
	}

}
