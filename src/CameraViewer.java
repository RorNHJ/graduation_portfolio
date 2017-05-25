/*intel에서 제공하는 3d카메라와 관련된 함수이다. 여기선 3D카메라의 얼굴추적 기능을 이용하여 얼굴을 인식할 경우 얼굴 크기에 비례해서
* 얼굴을 중심으로 사람 모형을 오버레이 시켜, 사람 주변의 배경을 삭제하고 사람의 모습만 추출한다.*/


import intel.rssdk.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

public class CameraViewer extends Thread
{

    static int cWidth  = 640;
    static int cHeight = 480;
  //  static int dWidth, dHeight;
    static boolean exit = false;
    BufferedImage streamingImage;
    BufferedImage becutted;


    public CameraViewer() {
        streamingImage = new BufferedImage(640 ,480,BufferedImage.TYPE_INT_ARGB);
        becutted = new BufferedImage(640,480,BufferedImage.TYPE_INT_ARGB);
    }


    /*3D카메라 장치가 제대로 인식되었는지, 컴퓨터와 연결 되었는지 확인하는 기본 함수*/
    private static void PrintConnectedDevices()
    {
        PXCMSession session = PXCMSession.CreateInstance();
        PXCMSession.ImplDesc desc = new PXCMSession.ImplDesc();
        PXCMSession.ImplDesc outDesc = new PXCMSession.ImplDesc();
        desc.group = EnumSet.of(PXCMSession.ImplGroup.IMPL_GROUP_SENSOR);
        desc.subgroup = EnumSet.of(PXCMSession.ImplSubgroup.IMPL_SUBGROUP_VIDEO_CAPTURE);

        int numDevices = 0; // 3d카메라 개체 수 (본인은 한 대만 사용할 것.)
        for (int i = 0; ;i++)
        {
            if (session.QueryImpl(desc, i, outDesc).isError())
                break;

            PXCMCapture capture = new PXCMCapture();
            if (session.CreateImpl(outDesc, capture).isError())
                continue;

            for (int j = 0; ;j++)
            {
                PXCMCapture.DeviceInfo info = new PXCMCapture.DeviceInfo();
                if (capture.QueryDeviceInfo(j, info).isError())
                    break;

                System.out.println(info.name);
                numDevices++; /*우리는 한 대만 사용 할거니까 당연히 이 값은 1 이다. */
            }
        }

        System.out.println("Found " + numDevices + " devices");
    }

    public void run() {
        PrintConnectedDevices();
        int[] cBuff;
        BufferedImage srcImg;
        int srcWidth = 0;
        int srcHeight = 0;
        int destWidth = 0, destHeight = 0;
        double t;
        double tx = 0;
        double ty = 0;

        Listener listener = new Listener();

        CameraViewer c_raw = new CameraViewer();
        DrawFrame c_df = new DrawFrame(cWidth  , cHeight);
        JFrame cframe= new JFrame("Intel(R) RealSense(TM) SDK - Color Stream");
        cframe.addWindowListener(listener);
        cframe.setSize(cWidth, cHeight);
        cframe.add(c_df);
        cframe.setVisible(true);

        try {
            // chromakey 이미지는 사람의 형체인 형광연두색의 이미지이다.
            srcImg = ImageIO.read(new File("C:\\Users\\user\\IdeaProjects\\work\\src\\chromakey.png"));
            srcWidth = srcImg.getWidth();
            srcHeight = srcImg.getHeight();


            PXCMSenseManager senseMgr = PXCMSenseManager.CreateInstance();

            /*얼굴 인식 기능 활성화 단계 */
            pxcmStatus sts = senseMgr.EnableStream(PXCMCapture.StreamType.STREAM_TYPE_COLOR, cWidth, cHeight);
            sts = senseMgr.EnableStream(PXCMCapture.StreamType.STREAM_TYPE_DEPTH);
            sts = senseMgr.EnableFace(null);
            PXCMFaceModule faceModule = senseMgr.QueryFace();
            sts = pxcmStatus.PXCM_STATUS_DATA_UNAVAILABLE;
            PXCMFaceConfiguration faceConfig = faceModule.CreateActiveConfiguration();
            faceConfig.SetTrackingMode(PXCMFaceConfiguration.TrackingModeType.FACE_MODE_COLOR_PLUS_DEPTH);
            faceConfig.detection.isEnabled = true;
            faceConfig.ApplyChanges();
            faceConfig.Update();

            sts = senseMgr.Init();
            System.out.println(sts);

            PXCMCapture.Device device = senseMgr.QueryCaptureManager().QueryDevice();
            PXCMCapture.Device.StreamProfileSet profiles = new PXCMCapture.Device.StreamProfileSet();

            device.QueryStreamProfileSet(profiles);
            PXCMFaceData faceData = faceModule.CreateOutput();
            //dWidth = profiles.depth.imageInfo.width;
           // dHeight = profiles.depth.imageInfo.height;

            System.out.println("cWidth  : " + cWidth);
            System.out.println("cHeight  : " + cHeight);
         //   System.out.println("dWidth  : " + dWidth);
          //  System.out.println("dHeight  : " + dHeight);


            if (sts == pxcmStatus.PXCM_STATUS_NO_ERROR) {
                while (true) {
                    sts = senseMgr.AcquireFrame(true);
                    PXCMCapture.Sample sample = senseMgr.QuerySample();
                    PXCMCapture.Sample faceSample = senseMgr.QueryFaceSample();
                    faceData.Update();
                    if (sts == pxcmStatus.PXCM_STATUS_NO_ERROR) {

                        PXCMImage.ImageData cData = new PXCMImage.ImageData();


                        sample.color.AcquireAccess(PXCMImage.Access.ACCESS_READ, PXCMImage.PixelFormat.PIXEL_FORMAT_RGB32, cData);

                        cBuff = new int[cData.pitches[0] / 4 * cHeight];

                        cData.ToIntArray(0, cBuff);

                        streamingImage.setRGB(0, 0, cWidth, cHeight, cBuff, 0, cData.pitches[0] / 4);
                        for (int fidx = 0; ; fidx++) {
                            PXCMFaceData.Face face = faceData.QueryFaceByIndex(fidx);
                            if (face == null) break;
                            PXCMFaceData.DetectionData detectData = face.QueryDetection();

                            if (detectData != null) {
                                PXCMRectI32 rect = new PXCMRectI32();
                                boolean ret = detectData.QueryBoundingRect(rect);
                                if (ret) {
                                    t = rect.w / 100.0;
                                    destWidth = (int) (srcWidth * t);
                                    destHeight = (int) (srcHeight * t);


                                    BufferedImage destImg = new BufferedImage(cWidth, cHeight, BufferedImage.TYPE_INT_ARGB);
                                    Graphics2D g = destImg.createGraphics();
                                    g.drawImage(streamingImage, 0, 0, null);
                                    g.drawImage(srcImg, rect.x - (int) (82 * t), rect.y - (int) (22 * t), destWidth, destHeight, null);

                                    int sBuff[] = new int[cData.pitches[0] / 4 * cHeight];
                                    sBuff = destImg.getRGB(0, 0, cWidth, cHeight, sBuff, 0, cWidth);

                                    //이미지 분할 프로세싱 (영상처리 부분에선 이 부분만 제가 했습니다. 나머지는 인텔에서 제공하는 함수 사용함)
                                    /*chromakey.png 이미지가 밝은 형광연두색인데 그 색을 제외한 나머지 부분은 배경임으로 그 부분을 투명하게 한다.*/
                                    for (int y = 0; y < sBuff.length; y++) {

                                        if (!(Integer.toHexString(sBuff[y]).equals("ff10ed06"))) { // 밝은 형광 연두색(16진수)


                                            cBuff[y] = 0; // 투명하게..

                                        }

                                    }
                                    c_df.image.setRGB(0, 0, cWidth, cHeight, cBuff, 0, cData.pitches[0] / 4);

                                    becutted.setRGB(0, 0, cWidth, cHeight, cBuff, 0, cData.pitches[0] / 4);

                                    c_df.repaint();


                                }
                            } else
                                break;

                            PXCMFaceData.PoseData poseData = face.QueryPose();
                            if (poseData != null) {
                                PXCMFaceData.PoseEulerAngles pea = new PXCMFaceData.PoseEulerAngles();
                                poseData.QueryPoseAngles(pea);
                                System.out.println("Pose Data at frame #");
                                System.out.println("(Roll, Yaw, Pitch) = (" + pea.roll + "," + pea.yaw + "," + pea.pitch + ")");
                            }
                        }

                        sts = sample.color.ReleaseAccess(cData);


                    } else {
                        System.out.println("Failed to acquire frame");
                    }

                    senseMgr.ReleaseFrame();

                }
            }

        } catch (IOException e) {
            e.getStackTrace();
        }
    }

}

class Listener extends WindowAdapter {
    public boolean exit = false;
    @Override public void windowClosing(WindowEvent e) {
        exit=true;
    }
}

class DrawFrame extends Component {
    public BufferedImage image;

    public DrawFrame(int width, int height) {

        image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);

    }

    public void paint(Graphics g) {


        ((Graphics2D)g).drawImage(image,0,0,this);

    }
}