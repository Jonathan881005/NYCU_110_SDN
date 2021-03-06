package nctu.winlab.vlanbasedsr;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Path;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NameConfigListener cfgListener = new NameConfigListener();

    private final ConfigFactory<ApplicationId, NameConfig> factory =
      new ConfigFactory<ApplicationId, NameConfig>(
          APP_SUBJECT_FACTORY, NameConfig.class, "VlanBasedSRConfig") {
        @Override
        public NameConfig createConfig() {
          return new NameConfig();
        }
      };
      
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;
    
    private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    private Processor processor = new Processor();
    
	Map<DeviceId, Integer> device_to_vlan = new HashMap();
	Map<IpPrefix, DeviceId> subnet_to_device = new HashMap();
	Map<Integer, ConnectPoint> host_to_CP = new HashMap();
	Map<Integer, MacAddress> host_to_mac = new HashMap();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.vlanbasedsr");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
		packetService.addProcessor(processor,PacketProcessor.director(2));
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        packetService.removeProcessor(processor);
        processor = null;
        cfgService.unregisterConfigFactory(factory);
        log.info("Stopped");
    }

    private class Processor implements PacketProcessor{
		@Override
		public void process(PacketContext context){
			if(context.isHandled()){
				return;
			}
        }
    }

    private class NameConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                && event.configClass().equals(NameConfig.class)) {
                NameConfig config = cfgService.getConfig(appId, NameConfig.class);
                if (config != null) {
                    device_to_vlan = config.getVlan();
                    subnet_to_device = config.getSubnet();
                    host_to_CP = config.getCP();
                    host_to_mac = config.getMac();

                    log.info("Get network topology, hehe");
                    for(IpPrefix sourceSubnet: subnet_to_device.keySet()){
                        log.info("{}", sourceSubnet);
                    }
                    Set_DstSubnet_Config();
                    Set_DstHost_Config();
                }
            }
        }
    }

    // ??? subnet ??? destination ??? rule
    private void Set_DstSubnet_Config(){

        for(IpPrefix dstSubnet: subnet_to_device.keySet()){ // ??????????????? subnet
			DeviceId dstId = subnet_to_device.get(dstSubnet);
			VlanId dstVlanId = VlanId.vlanId(device_to_vlan.get(dstId).shortValue());

            // ????????? rule
			for(DeviceId otherId : device_to_vlan.keySet()){
                // ??? subnet ?????? dst, ???????????? device ??????
				if(dstId.equals(otherId))
					continue;

				// ???????????? path
				Set<Path> paths;
				paths = topologyService.getPaths(topologyService.currentTopology(),
                            otherId,
							dstId);
                            
                // ???????????? VlanID ??? forward ??? rule
				for(Path p : paths){
					install_Vlan_PassRule(dstVlanId, otherId, p.src().port());
					break;
					
				}
			}

            // ????????? rule
			for(IpPrefix otherSubnet: subnet_to_device.keySet()){
                // ??? subnet ?????? dst, ????????? subnet ??????
				if(dstSubnet.equals(otherSubnet))
					continue;

                // ????????? subnet ??? swid, ????????????
				DeviceId otherSubnetId = subnet_to_device.get(otherSubnet);

				// ????????? path
				Set<Path> paths;
				paths = topologyService.getPaths(topologyService.currentTopology(),
                            otherSubnetId,
                            dstId);

				// ????????? ?????? VlanID ??????????????? rule
				for(Path p : paths){
					install_Vlan_PushRule(dstSubnet, dstVlanId, otherSubnetId, p.src().port());
					break;
					
				}
	
			}

		}
    }

    // InterSubnet(Vlan), ????????? rule 
    private void install_Vlan_PassRule(VlanId dstVlanId, DeviceId sw, PortNumber out_port){

		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
			.matchVlanId(dstVlanId);
            
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.setOutput(out_port).build();

		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(40000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
            .makePermanent()
			.add();

		flowObjectiveService.forward(sw, forwardingObjective);
    }

    // InterSubnet(Vlan), ????????? rule
	private void install_Vlan_PushRule(IpPrefix dstSubnet, VlanId dstVlanId, DeviceId srcId, PortNumber out_port){

		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
			.matchIPDst(dstSubnet);
            
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.pushVlan()
			.setVlanId(dstVlanId)
			.setOutput(out_port).build();

		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(40000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
            .makePermanent()
			.add();

		flowObjectiveService.forward(srcId, forwardingObjective);
	}

    // ??? host(Mac) ??? destination ??? rule
	private void Set_DstHost_Config(){
		for(Integer hostNum : host_to_CP.keySet()){

            // IntraSubnet ??? rule, single switch
			MacAddress dstMac = host_to_mac.get(hostNum);
			ConnectPoint dstCP = host_to_CP.get(hostNum);
			DeviceId dstId = dstCP.deviceId();
			PortNumber out_port = dstCP.port();
			install_IntraSubnet_Rule(dstMac, dstId, out_port);

            // ?????? subnet ??????????????? host, ??????????????????????????? rule
			VlanId dstVlanId = VlanId.vlanId(device_to_vlan.get(dstId).shortValue());
			install_Vlan_PopRule(dstVlanId, dstMac, dstId, out_port);
		}
	}

    // InterSubnet(Vlan), ????????? rule
	private void install_Vlan_PopRule(VlanId dstVlanId, MacAddress dstMac, DeviceId dstId, PortNumber out_port){

		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
			.matchVlanId(dstVlanId)
			.matchEthDst(dstMac);
            
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.popVlan()
			.setOutput(out_port).build();

		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(40000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
            .makePermanent()
			.add();

		flowObjectiveService.forward(dstId,forwardingObjective);

	}

    // IntraSubnet, ????????? single switch ??? rule
    private void install_IntraSubnet_Rule(MacAddress dstMac, DeviceId dstId, PortNumber out_port){
        
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		selectorBuilder.matchEthDst(dstMac);
        
		TrafficTreatment treatmentBuilder = DefaultTrafficTreatment.builder()
			.setOutput(out_port).build();

		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuilder)
			.withPriority(39999)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
            .makePermanent()
			.add();

		flowObjectiveService.forward(dstId, forwardingObjective);

	}

}
