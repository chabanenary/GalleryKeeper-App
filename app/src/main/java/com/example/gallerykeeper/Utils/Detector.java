package com.example.gallerykeeper.Utils;


import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class Detector {

    private Interpreter interpreter;
    private List<String> labels = new ArrayList<>();

    private int tensorWidth = 0;
    private int tensorHeight = 0;
    private int numChannel = 0;
    private int numElements = 0;

    private final ImageProcessor imageProcessor;

    private final Context context;
    private final String modelPath;
    private final String labelPath;

    private static final float INPUT_MEAN = 0f;
    private static final float INPUT_STANDARD_DEVIATION = 255f;
    private static final DataType INPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final float CONFIDENCE_THRESHOLD = 0.3F;
    private static final float IOU_THRESHOLD = 0.5F;


    public Detector(Context context, String modelPath, String labelPath) {
        this.context = context;
        this.modelPath = modelPath;
        this.labelPath = labelPath;

        imageProcessor = new ImageProcessor.Builder()
                .add(new NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
                .add(new CastOp(INPUT_IMAGE_TYPE))
                .build();

        CompatibilityList compatList = new CompatibilityList();

        Interpreter.Options options = new Interpreter.Options();
        if (compatList.isDelegateSupportedOnThisDevice()) {
            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            options.addDelegate(new GpuDelegate(delegateOptions));
        } else {
            options.setNumThreads(4);
        }

        try {
            java.nio.MappedByteBuffer model = loadModelBuffer(context, modelPath);
            interpreter = new Interpreter(model, options);

            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();

            if (inputShape != null) {
                tensorWidth = inputShape[1];
                tensorHeight = inputShape[2];

                // If in case input shape is in format of [1, 3, ..., ...]
                if (inputShape[1] == 3) {
                    tensorWidth = inputShape[2];
                    tensorHeight = inputShape[3];
                }
            }

            if (outputShape != null) {
                numChannel = outputShape[1];
                numElements = outputShape[2];
            }

            labels = loadLabelsFlexible(context, labelPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void restart(boolean isGpu) {
        interpreter.close();

        Interpreter.Options options;
        if (isGpu) {
            CompatibilityList compatList = new CompatibilityList();
            options = new Interpreter.Options();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
                options.addDelegate(new GpuDelegate(delegateOptions));
            } else {
                options.setNumThreads(4);
            }
        } else {
            options = new Interpreter.Options();
            options.setNumThreads(4);
        }

        try {
            java.nio.MappedByteBuffer model = loadModelBuffer(context, modelPath);
            interpreter = new Interpreter(model, options);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static java.nio.MappedByteBuffer loadModelBuffer(Context ctx, String modelPath) throws IOException {
        File f = new File(modelPath);
        if (f.isAbsolute() && f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                java.nio.channels.FileChannel fileChannel = fis.getChannel();
                return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
            }
        }
        return FileUtil.loadMappedFile(ctx, modelPath);
    }

    private static List<String> loadLabelsFlexible(Context ctx, String labelsPath) throws IOException {
        List<String> out = new ArrayList<>();
        File f = new File(labelsPath);
        InputStream is = null;
        try {
            if (f.isAbsolute() && f.exists()) {
                is = new FileInputStream(f);
            } else {
                is = ctx.getAssets().open(labelsPath);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) out.add(line);
            }
            reader.close();
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
        return out;
    }

    public void close() {
        interpreter.close();
    }


    public List<BoundingBox>  detect(Bitmap frame) {
        if (tensorWidth == 0) return null;
        if (tensorHeight == 0) return null;
        if (numChannel == 0) return null;
        if (numElements == 0) return null;

        long inferenceTime = SystemClock.uptimeMillis();

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false);

        TensorImage tensorImage = new TensorImage(INPUT_IMAGE_TYPE);
        tensorImage.load(resizedBitmap);
        TensorImage processedImage = imageProcessor.process(tensorImage);
        java.nio.ByteBuffer imageBuffer = processedImage.getBuffer();

        TensorBuffer output = TensorBuffer.createFixedSize(new int[]{1, numChannel, numElements}, OUTPUT_IMAGE_TYPE);
        interpreter.run(imageBuffer, output.getBuffer());

        List<BoundingBox> bestBoxes = bestBox(output.getFloatArray());
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
        return bestBoxes;
    }

    private List<BoundingBox> bestBox(float[] array) {

        List<BoundingBox> boundingBoxes = new ArrayList<>();

        for (int c = 0; c < numElements; c++) {
            float maxConf = CONFIDENCE_THRESHOLD;
            int maxIdx = -1;
            int j = 4;
            int arrayIdx = c + numElements * j;
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx];
                    maxIdx = j - 4;
                }
                j++;
                arrayIdx += numElements;
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                String clsName = labels.get(maxIdx);
                float cx = array[c]; // 0
                float cy = array[c + numElements]; // 1
                float w = array[c + numElements * 2];
                float h = array[c + numElements * 3];
                float x1 = cx - (w / 2F);
                float y1 = cy - (h / 2F);
                float x2 = cx + (w / 2F);
                float y2 = cy + (h / 2F);
                if (x1 < 0F || x1 > 1F) continue;
                if (y1 < 0F || y1 > 1F) continue;
                if (x2 < 0F || x2 > 1F) continue;
                if (y2 < 0F || y2 > 1F) continue;

                boundingBoxes.add(
                        new BoundingBox(
                                x1, y1, x2, y2,
                                cx, cy, w, h,
                                maxConf, maxIdx, clsName
                        )
                );
            }
        }

        if (boundingBoxes.isEmpty()) return null;

        return applyNMS(boundingBoxes);
    }

    private List<BoundingBox> applyNMS(List<BoundingBox> boxes) {
        List<BoundingBox> sortedBoxes = new ArrayList<>(boxes);
        Collections.sort(sortedBoxes, Comparator.comparingDouble(box -> -box.cnf)); // Sort descending by confidence
        List<BoundingBox> selectedBoxes = new ArrayList<>();

        while (!sortedBoxes.isEmpty()) {
            BoundingBox first = sortedBoxes.get(0);
            selectedBoxes.add(first);
            sortedBoxes.remove(first);

            Iterator<BoundingBox> iterator = sortedBoxes.iterator();
            while (iterator.hasNext()) {
                BoundingBox nextBox = iterator.next();
                float iou = calculateIoU(first, nextBox);
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove();
                }
            }
        }

        return selectedBoxes;
    }

    private float calculateIoU(BoundingBox box1, BoundingBox box2) {
        float x1 = Math.max(box1.x1, box2.x1);
        float y1 = Math.max(box1.y1, box2.y1);
        float x2 = Math.min(box1.x2, box2.x2);
        float y2 = Math.min(box1.y2, box2.y2);
        float intersectionArea = Math.max(0F, x2 - x1) * Math.max(0F, y2 - y1);
        float box1Area = box1.w * box1.h;
        float box2Area = box2.w * box2.h;
        return intersectionArea / (box1Area + box2Area - intersectionArea);
    }
}