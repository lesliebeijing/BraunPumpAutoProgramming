package com.lesliefang;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class AutoProgramming {

    public int[] downloadProposal(String spaceComIp, int port, String proposalListXml) {
        FutureTask<int[]> futureTask = new FutureTask<>(new SocketCallable(spaceComIp, port, proposalListXml));
        new Thread(futureTask).start();
        try {
            int[] ret = futureTask.get(8, TimeUnit.SECONDS);
            System.out.println("downloadProposal status " + ret[0] + " error " + ret[1]);
            return ret;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new int[]{-1, -1};
    }

    public boolean downloadProposalList(String spaceComIp, int port, List<DrugProposal> proposalList) {
        if (proposalList == null) {
            return false;
        }
        String proposalListXml = constructProposalListXml(proposalList);
        if (proposalListXml == null) {
            return false;
        }
        int[] ret = downloadProposal(spaceComIp, port, proposalListXml);
        int status = ret[0];
        int error = ret[1];

        return (status == 0 && error == 0);
    }

    public String constructProposalListXml(List<DrugProposal> proposalList) {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.newDocument();
            document.setXmlStandalone(true);
            /*
             *     <Drug>
             *         <DrugId>12345</DrugId>
             *         <DrugName>Dopamin</DrugName>
             *         <DrugShort>Dopa0.1</DrugShort>
             *         <InfusionRate>2</InfusionRate>
             *         <RateUnit>ml/h</RateUnit>
             *         <Checksum>58769</Checksum>
             *     </Drug>
             */
            Element rootElement = document.createElement("ProposalList");
            rootElement.setAttribute("xmlns", "http://www.bbraun.com/HC/AIS/Space/AutoProgramming");
            document.appendChild(rootElement);

            String checkSums = "";
            for (DrugProposal proposal : proposalList) {
                Element drug = document.createElement("Drug");
                String drugContent = "";
                if (proposal.getDrugId() != null) {
                    Element drugId = document.createElement("DrugId");
                    drugId.setTextContent(proposal.getDrugId());
                    drug.appendChild(drugId);
                    drugContent += proposal.getDrugId();
                }
                if (proposal.getDrugName() != null) {
                    Element drugName = document.createElement("DrugName");
                    drugName.setTextContent(proposal.getDrugName());
                    drug.appendChild(drugName);
                    drugContent += proposal.getDrugName();
                }
                if (proposal.getDrugShort() != null) {
                    Element drugShort = document.createElement("DrugShort");
                    drugShort.setTextContent(proposal.getDrugShort());
                    drug.appendChild(drugShort);
                    drugContent += proposal.getDrugShort();
                }
                if (proposal.getRate() != null) {
                    Element rate = document.createElement("InfusionRate");
                    rate.setTextContent(proposal.getRate());
                    drug.appendChild(rate);
                    Element rateUnit = document.createElement("RateUnit");
                    rateUnit.setTextContent("ml/h");
                    drug.appendChild(rateUnit);
                    drugContent += proposal.getRate();
                    drugContent += "ml/h";
                }
                if (proposal.getVtbi() != null) {
                    Element vtbi = document.createElement("VtbiValue");
                    vtbi.setTextContent(proposal.getVtbi());
                    drug.appendChild(vtbi);
                    Element vtbiUnit = document.createElement("VtbiUnit");
                    vtbiUnit.setTextContent("ml");
                    drug.appendChild(vtbiUnit);
                    drugContent += proposal.getVtbi();
                    drugContent += "ml";
                }
                if (proposal.getOrderNumber() != null) {
                    Element orderNumber = document.createElement("OrderNumber");
                    orderNumber.setTextContent(proposal.getOrderNumber());
                    drug.appendChild(orderNumber);
                    drugContent += proposal.getOrderNumber();
                }
                int drugCrc = CRC16CCITT.calculate(CRC16CCITT.CRC16BEGIN, drugContent);
                Element checksum = document.createElement("Checksum");
                checksum.setTextContent(drugCrc + "");
                drug.appendChild(checksum);
                checkSums += drugCrc + "";

                rootElement.appendChild(drug);
            }
            Element checksumTotal = document.createElement("ChecksumTotal");
            int totalCrc = CRC16CCITT.calculate(CRC16CCITT.CRC16BEGIN, checkSums);
            checksumTotal.setTextContent(totalCrc + "");
            rootElement.appendChild(checksumTotal);

            TransformerFactory tff = TransformerFactory.newInstance();
            Transformer transformer = tff.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(bos));
            return bos.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    class SocketCallable implements Callable<int[]> {
        String proposalListXml;
        String spaceComIp;
        int port;

        SocketCallable(String spaceComIp, int port, String proposalListXml) {
            this.spaceComIp = spaceComIp;
            this.proposalListXml = proposalListXml;
            this.port = port;
        }

        @Override
        public int[] call() throws Exception {
            Socket socket = null;
            InputStream inputStream = null;
            try {
                socket = new Socket();
                SocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName(spaceComIp), port);
                socket.connect(socketAddress, 5000);
                inputStream = socket.getInputStream();
            } catch (Exception e) {
                e.printStackTrace();
                if (inputStream != null) {
                    inputStream.close();
                }
                if (socket != null) {
                    socket.close();
                }
                return new int[]{-1, -1};
            }

            OutputStream outputStream;
            try {
                outputStream = socket.getOutputStream();
                outputStream.write(proposalListXml.getBytes(Charset.forName("utf-8")));
            } catch (IOException e) {
                e.printStackTrace();
                socket.close();
                return new int[]{-1, -1};
            }

            byte[] buf = new byte[1024];
            try {
                // 未考虑读半包问题，假设每次都能读取到完整的 xml
                int length = inputStream.read(buf);
                String str = new String(buf, 0, length);
                System.out.println("111111111111111 " + str);
                return parseXml(str);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    outputStream.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("222222222222222222 thread finish");
            return new int[]{-1, -1};
        }
    }

    private int[] parseXml(String responseXml) {
        int status = -1;
        int error = -1;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(responseXml.getBytes()));
            Element rootElement = document.getDocumentElement();
            NodeList statusList = rootElement.getElementsByTagName("Status");
            if (statusList != null && statusList.getLength() > 0) {
                status = Integer.parseInt(statusList.item(0).getTextContent());
            }
            NodeList errorList = rootElement.getElementsByTagName("Error");
            if (errorList != null && errorList.getLength() > 0) {
                error = Integer.parseInt(errorList.item(0).getTextContent());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new int[]{status, error};
    }

    public static void main(String[] args) {
        String s = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<ProposalListResponse xmlns=\"http://www.bbraun.com/HC/AIS/Space/AutoProgramming\">\n" +
                "    <Status>1</Status>\n" +
                "    <Error>68</Error>\n" +
                "    <ProposalList >\n" +
                "        <Drug>\n" +
                "            <DrugId>12345</DrugId>\n" +
                "            <DrugName>Dopamin</DrugName>\n" +
                "            <DrugShort>Dopa0.1</DrugShort>\n" +
                "            <InfusionRate>2</InfusionRate>\n" +
                "            <RateUnit>ml/h</RateUnit>\n" +
                "            <Checksum>58769</Checksum>\n" +
                "        </Drug>\n" +
                "        <ChecksumTotal>58769</ChecksumTotal>\n" +
                "    </ProposalList>\n" +
                "</ProposalListResponse>";

        AutoProgramming autoProgram = new AutoProgramming();
        List<DrugProposal> proposalList = new ArrayList<>();
        DrugProposal drug1 = new DrugProposal();
        drug1.setDrugId("22222");
        drug1.setDrugName("Lasix 0.25/501");
        drug1.setDrugShort("0.005Lasix");
        drug1.setRate("0.20");
        proposalList.add(drug1);
        String xml = autoProgram.constructProposalListXml(proposalList);
        System.out.println(xml);

        autoProgram.downloadProposal("172.17.41.149", 4002, xml);
    }
}
