package nctu.winlab.ProxyArp;

import java.util.Map;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Optional;


import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent{

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgeportService;

    private ProxyArpPacketProcessor processor = new ProxyArpPacketProcessor();

    private Map<Ip4Address, MacAddress> ip_to_mac = new HashMap<Ip4Address, MacAddress>();
    private Map<MacAddress, DeviceId> mac_to_device = new HashMap<MacAddress, DeviceId>();
    private Map<MacAddress, PortNumber> mac_to_port = new HashMap<MacAddress, PortNumber>();

    private ApplicationId appId;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.ProxyArp");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        packetService.requestPackets(buildArpSelector(), PacketPriority.REACTIVE, appId);
        log.info("Proxy Arp Started!╰( ͡° ͜ʖ ͡° )つ──☆*:・ﾟ");
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;
        packetService.cancelPackets(buildArpSelector(), PacketPriority.REACTIVE, appId);

        log.info("Proxy Arp Stopped!╰( ͡° ͜ʖ ͡° )つ──☆*:・ﾟ");
    }

    private class ProxyArpPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) 
                return;
            else
                context.block();
            
            InboundPacket pkt = context.inPacket();
            OutboundPacket outpkt = context.outPacket();
            Ethernet eth = pkt.parsed();
            if (eth == null)
                return;

            short type = eth.getEtherType();
            if(type != Ethernet.TYPE_ARP)
                return;

            ARP arppkt = (ARP) eth.getPayload();
            Short op = arppkt.getOpCode();
            Integer opi = 0+op;

            MacAddress srcMAC = eth.getSourceMAC();
            MacAddress dstMAC = eth.getDestinationMAC();
            
            PortNumber in_port= context.inPacket().receivedFrom().port();
            DeviceId   sw     = context.inPacket().receivedFrom().deviceId(); 
            // log.info("{}, {}, {}", eth, eth.getPayload(), eth.getPayload().getPayload());
            // log.info("{}, ", eth.getPayload().toString());

            Ip4Address src_ip = Ip4Address.valueOf(arppkt.getSenderProtocolAddress());
            Ip4Address dst_ip = Ip4Address.valueOf(arppkt.getTargetProtocolAddress());

            if(!mac_to_device.containsKey(srcMAC)){ // 沒有這個mac -> 記住 + Flooding
                mac_to_device.put(srcMAC, sw);
                mac_to_port.put(srcMAC, in_port);
                ip_to_mac.put(src_ip, srcMAC);
            }

            if(opi.equals(1)){ // Request

                // 查 ip!
                if(!ip_to_mac.containsKey(dst_ip)){ // 沒有這個 ip ->  Flooding
                    log.info("TABLE MISS. Send request to edge ports");
                    FloodingAllEdgePort(context, outpkt.sendThrough(), outpkt.inPort());
                }

                else{ // 有這個 ip -> 直接發回 ArpReply
                    dstMAC = ip_to_mac.get(dst_ip);
                    log.info("TABLE HIT. Requested MAC = {}", dstMAC);
                    MakeAndReply(sw, in_port, dstMAC, dst_ip, eth);
                }
            }
            else{   // Reply -> 記 ip (了)然後傳回去
                log.info("RECV REPLY. Requested MAC = {}", srcMAC);
                JumpToThePort(dstMAC, context);
            }
        }
    }

    private void MakeAndReply(DeviceId sw, PortNumber in_port, MacAddress dstMAC, Ip4Address dst_ip, Ethernet eth){
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(in_port)
                .build();

        Ethernet newpkt = ARP.buildArpReply(dst_ip, dstMAC, eth);

        DefaultOutboundPacket packet = new DefaultOutboundPacket(sw, treatment, ByteBuffer.wrap(newpkt.serialize()));
        packetService.emit(packet);
    }

    private void FloodingAllEdgePort(PacketContext context, DeviceId senderDevice, PortNumber senderPort){
        Iterable<ConnectPoint> ic = edgeportService.getEdgePoints();
        for (ConnectPoint c : ic) {
            if(c.port() == null)
                log.info("error! c.port() = null!! in FloodingAllEdgePort!");
            
            if(c.deviceId().equals(senderDevice) && c.port().equals(senderPort))
                continue;
            
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(c.port())
                .build();

            DefaultOutboundPacket packet = new DefaultOutboundPacket(c.deviceId(), treatment, ByteBuffer.wrap(context.inPacket().parsed().serialize()));
            packetService.emit(packet);
        }
    }

    private void JumpToThePort(MacAddress mac, PacketContext context){
        DeviceId dest_device = mac_to_device.get(mac);
        PortNumber dest_port = PortNumber.portNumber("0");
        if(mac_to_port.get(mac) != null){
            dest_port = mac_to_port.get(mac);
        }
        else{
            log.info("error! mac_to_port.get({}) = null!! in JumpToThePort", mac);
        }

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(dest_port)
                .build();

        DefaultOutboundPacket packet = new DefaultOutboundPacket(dest_device, treatment, ByteBuffer.wrap(context.inPacket().parsed().serialize()));
        packetService.emit(packet);
    }


    private TrafficSelector buildArpSelector() {
        return DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP)
                .build();
    }

}
