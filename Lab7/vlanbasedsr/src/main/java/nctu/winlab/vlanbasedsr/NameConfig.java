package nctu.winlab.vlanbasedsr;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import com.fasterxml.jackson.databind.JsonNode; 

public class NameConfig extends Config<ApplicationId> {

	public static final String vlan = "Vlan";
	public static final String subnet = "Subnet";
	public static final String hostcp = "hostCP";
	public static final String hostmac = "hostMac";

  @Override
  public boolean isValid() {
    return hasOnlyFields(vlan, subnet, hostcp, hostmac);
  }

  // public String name() {
  //   return get(NAME, null);
  // }

  public Map<DeviceId, Integer> getVlan(){
		Map<DeviceId, Integer> device_to_vlan = new HashMap();
		
		JsonNode vlanNode = node().get("Vlan");
		Iterator<String> iter = vlanNode.fieldNames();
		while( iter.hasNext() ){
			String key = iter.next();
			DeviceId sw = DeviceId.deviceId(key);
			int vlanId = Integer.valueOf(vlanNode.get(key).asText("-1"));
			device_to_vlan.put(sw, vlanId);
		}
		return device_to_vlan;
	}

	public Map<IpPrefix, DeviceId> getSubnet(){
		Map<IpPrefix, DeviceId> subnet_to_device = new HashMap();
		
		JsonNode subnetNode = node().get("Subnet");
		Iterator<String> iter = subnetNode.fieldNames();
		while( iter.hasNext() ){
			String key = iter.next();
			IpPrefix subnet = IpPrefix.valueOf(key);
			DeviceId sw = DeviceId.deviceId(subnetNode.get(key).asText(""));
			subnet_to_device.put(subnet, sw);
		}
		return subnet_to_device;

	}
	public Map<Integer, ConnectPoint> getCP(){
		Map<Integer, ConnectPoint> host_to_CP = new HashMap();
		
		JsonNode cpNode = node().get("hostCP");
		Iterator<String> iter = cpNode.fieldNames();
		while( iter.hasNext() ){
			String key = iter.next();
			int hostNum = Integer.valueOf(key);
			ConnectPoint cp = ConnectPoint.deviceConnectPoint(cpNode.get(key).asText(""));
			host_to_CP.put(hostNum, cp);
		}
		return host_to_CP;

	}
	public Map<Integer, MacAddress> getMac(){
		Map<Integer, MacAddress> host_to_mac = new HashMap();
		
		JsonNode macNode = node().get("hostMac");
		Iterator<String> iter = macNode.fieldNames();
		while( iter.hasNext() ){
			String key = iter.next();
			int hostNum = Integer.valueOf(key);
			MacAddress mac = MacAddress.valueOf(macNode.get(key).asText(""));
			host_to_mac.put(hostNum, mac);
		}
		return host_to_mac;

	}
}
