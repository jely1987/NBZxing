package com.ailiwean.core.zxing.core.qrcode.detector;

import android.os.Build;

import com.ailiwean.core.zxing.core.DecodeHintType;
import com.ailiwean.core.zxing.core.NotFoundException;
import com.ailiwean.core.zxing.core.ResultPoint;
import com.ailiwean.core.zxing.core.ResultPointCallback;
import com.ailiwean.core.zxing.core.common.BitMatrix;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @Package: com.ailiwean.core.zxing.core.qrcode.detector
 * @ClassName: FinderPatternFinder2
 * @Description:
 * @Author: SWY
 * @CreateDate: 2020/9/19 7:56 PM
 */
class FinderPatternFinder2 {

    private static final int CENTER_QUORUM = 2;
    private static final FinderPatternFinder2.EstimatedModuleComparator moduleComparator = new FinderPatternFinder2.EstimatedModuleComparator();
    protected static final int MIN_SKIP = 3; // 1 pixel/module times 3 modules/center
    protected static final int MAX_MODULES = 97; // support up to version 20 for mobile clients

    private final BitMatrix image;
    private final List<FinderPattern> possibleCenters;
    private boolean hasSkipped;
    private final int[] crossCheckStateCount;
    private final int[] clearStateCount;
    private final ResultPointCallback resultPointCallback;

    /**
     * <p>Creates a finder that will search the image for three finder patterns.</p>
     *
     * @param image image to search
     */
    public FinderPatternFinder2(BitMatrix image) {
        this(image, null);
    }

    public FinderPatternFinder2(BitMatrix image, ResultPointCallback resultPointCallback) {
        this.image = image;
        this.possibleCenters = new ArrayList<>();
        this.crossCheckStateCount = new int[5];
        clearStateCount = new int[5];
        this.resultPointCallback = resultPointCallback;
    }

    protected final BitMatrix getImage() {
        return image;
    }

    protected final List<FinderPattern> getPossibleCenters() {
        return possibleCenters;
    }

    final FinderPatternInfo find(Map<DecodeHintType, ?> hints) throws NotFoundException {
        boolean tryHarder = hints != null && hints.containsKey(DecodeHintType.TRY_HARDER);
        int maxI = image.getHeight();
        int maxJ = image.getWidth();
        //?????????????????????/???/???/???/?????????
        //1:1:3:1:1?????????????????????????????????????????????????????????????????????
        //??????????????????????????????????????????????????????
        //??????????????????????????????3?????????????????????????????????
        //???????????????????????????????????????????????????????????????????????????????????????
        //QR????????????????????????????????????
        int iSkip = (3 * maxI) / (4 * MAX_MODULES);
        if (iSkip < MIN_SKIP || tryHarder) {
            iSkip = MIN_SKIP;
        }

        boolean done = false;
        int[] stateCount = new int[5];
        for (int i = iSkip - 1; i < maxI && !done; i += iSkip) {
            // Get a row of black/white values
            doClearCounts(stateCount);
            int currentState = 0;
            for (int j = 0; j < maxJ; j++) {
                if (image.get(j, i)) {
                    // Black pixel
                    if ((currentState & 1) == 1) { // Counting white pixels
                        currentState++;
                    }
                    stateCount[currentState]++;
                } else { // White pixel
                    if ((currentState & 1) == 0) { // Counting black pixels
                        if (currentState == 4) { // A winner?

                            if (foundPatternCross(clearBlackEdgeCount(stateCount))) { // Yes
                                boolean confirmed = handlePossibleCenter(clearBlackEdgeCount(stateCount), i, j - getBlackEdgeOffset(stateCount));
                                if (confirmed) {
                                    // Start examining every other line. Checking each line turned out to be too
                                    // expensive and didn't improve performance.
                                    iSkip = 2;
                                    if (hasSkipped) {
                                        done = haveMultiplyConfirmedCenters();
                                    } else {
                                        int rowSkip = findRowSkip();
                                        if (rowSkip > stateCount[2]) {
                                            //?????????????????????????????????
                                            //????????????????????????????????????
                                            //?????????????????????????????????????????????
                                            //???????????????????????????????????????
                                            //?????????????????????????????????[2]????????????????????????????????????
                                            //???????????????????????????????????????????????????iSkip??????
                                            //??????????????????
                                            i += rowSkip - stateCount[2] - iSkip;
                                            j = maxJ - 1;
                                        }
                                    }
                                } else {
                                    doShiftCounts2(stateCount);
                                    currentState = 3;
                                    continue;
                                }
                                // Clear state to start looking again
                                currentState = 0;
                                doClearCounts(stateCount);
                            } else { // No, shift counts back by two
                                doShiftCounts2(stateCount);
                                currentState = 3;
                            }
                        } else {
                            stateCount[++currentState]++;
                        }
                    } else { // Counting white pixels
                        stateCount[currentState]++;
                    }
                }
            }
            if (foundPatternCross(clearBlackEdgeCount(stateCount))) {
                boolean confirmed = handlePossibleCenter(clearBlackEdgeCount(stateCount), i, maxJ - getBlackEdgeOffset(stateCount));
                if (confirmed) {
                    iSkip = stateCount[0];
                    if (hasSkipped) {
                        // Found a third one
                        done = haveMultiplyConfirmedCenters();
                    }
                }
            }
        }
        FinderPattern[] patternInfo = selectBestPatterns();
        ResultPoint.orderBestPatterns(patternInfo);
        return new FinderPatternInfo(patternInfo);
    }

    /***
     * ????????????
     * @param statusCounts
     * @return
     */
    private int[] clearBlackEdgeCount(int[] statusCounts) {
        doClearCopy(statusCounts);
        clearStateCount[0] = clearStateCount[4] = (int) (Math.min(clearStateCount[0], clearStateCount[4]));
        return clearStateCount;
    }

    private void doClearCopy(int[] stateCount) {
        System.arraycopy(stateCount, 0, clearStateCount, 0, stateCount.length);
    }


    /***
     *  ?????????????????????????????????
     * @param statusCounts
     * @return
     */
    private int getBlackEdgeOffset(int[] statusCounts) {
        if (statusCounts[0] / (float) statusCounts[4] > 2f ||
                statusCounts[0] / (float) statusCounts[4] < 0.5f) {
            if (statusCounts[4] > statusCounts[0])
                return statusCounts[4] - statusCounts[0];
        }
        return 0;
    }

    /**
     * ????????????????????????/???/???/???/?????????????????????????????????
     * ?????????????????????????????????
     */
    private static float centerFromEnd(int[] stateCount, int end) {
        return (end - stateCount[4] - stateCount[3]) - stateCount[2] / 2.0f;
    }

    /**
     * @param stateCount ???????????????/???/???/???/???????????????
     *                   ???finder??????????????????????????????
     */
    protected static boolean foundPatternCross(int[] stateCount) {
        int totalModuleSize = 0;
        for (int i = 0; i < 5; i++) {
            int count = stateCount[i];
            if (count == 0) {
                return false;
            }
            totalModuleSize += count;
        }
        if (totalModuleSize < 14) {
            return false;
        }

        float moduleSize = totalModuleSize / 7.0f;
        float maxVariance = moduleSize / 2.0f;
        // Allow less than 50% variance from 1-1-3-1-1 proportions
        return
                Math.abs(moduleSize - stateCount[0]) < maxVariance &&
                        Math.abs(moduleSize - stateCount[1]) < maxVariance &&
                        Math.abs(3.0f * moduleSize - stateCount[2]) < 3 * maxVariance &&
                        Math.abs(moduleSize - stateCount[3]) < maxVariance &&
                        Math.abs(moduleSize - stateCount[4]) < maxVariance;
    }

    /**
     * @param stateCount ???????????????/???/???/???/???????????????
     * @return true?????????????????????????????????1/1/3/1/1?????????
     * ???finder??????????????????????????????
     */
    protected static boolean foundPatternDiagonal(int[] stateCount) {
        int totalModuleSize = 0;
        for (int i = 0; i < 5; i++) {
            int count = stateCount[i];
            if (count == 0) {
                return false;
            }
            totalModuleSize += count;
        }
        if (totalModuleSize < 14) {
            return false;
        }
        float moduleSize = totalModuleSize / 7.0f;
        float maxVariance = moduleSize / 1.333f;
        // Allow less than 75% variance from 1-1-3-1-1 proportions
        return
                Math.abs(moduleSize - stateCount[0]) < maxVariance &&
                        Math.abs(moduleSize - stateCount[1]) < maxVariance &&
                        Math.abs(3.0f * moduleSize - stateCount[2]) < 3 * maxVariance &&
                        Math.abs(moduleSize - stateCount[3]) < maxVariance &&
                        Math.abs(moduleSize - stateCount[4]) < maxVariance;
    }

    private int[] getCrossCheckStateCount() {
        doClearCounts(crossCheckStateCount);
        return crossCheckStateCount;
    }

    @Deprecated
    protected final void clearCounts(int[] counts) {
        doClearCounts(counts);
    }

    @Deprecated
    protected final void shiftCounts2(int[] stateCount) {
        doShiftCounts2(stateCount);
    }

    protected static void doClearCounts(int[] counts) {
        Arrays.fill(counts, 0);
    }

    protected static void doShiftCounts2(int[] stateCount) {
        stateCount[0] = stateCount[2];
        stateCount[1] = stateCount[3];
        stateCount[2] = stateCount[4];
        stateCount[3] = 1;
        stateCount[4] = 0;
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     * ???????????????????????????????????????????????????????????????
     * finder?????????????????????????????????????????????
     *
     * @param centerI ???????????????????????????????????????
     * @param centerJ ???????????????????????????????????????????????????
     * @return true??????????????????????????????
     */
    private boolean crossCheckDiagonal(int centerI, int centerJ) {
        int[] stateCount = getCrossCheckStateCount();

        // Start counting up, left from center finding black center mass
        int i = 0;
        while (centerI >= i && centerJ >= i && image.get(centerJ - i, centerI - i)) {
            stateCount[2]++;
            i++;
        }
        if (stateCount[2] == 0) {
            return false;
        }

        // Continue up, left finding white space
        while (centerI >= i && centerJ >= i && !image.get(centerJ - i, centerI - i)) {
            stateCount[1]++;
            i++;
        }
        if (stateCount[1] == 0) {
            return false;
        }

        // Continue up, left finding black border
        while (centerI >= i && centerJ >= i && image.get(centerJ - i, centerI - i)) {
            stateCount[0]++;
            i++;
        }
        if (stateCount[0] == 0) {
            return false;
        }

        int maxI = image.getHeight();
        int maxJ = image.getWidth();

        // Now also count down, right from center
        i = 1;
        while (centerI + i < maxI && centerJ + i < maxJ && image.get(centerJ + i, centerI + i)) {
            stateCount[2]++;
            i++;
        }

        while (centerI + i < maxI && centerJ + i < maxJ && !image.get(centerJ + i, centerI + i)) {
            stateCount[3]++;
            i++;
        }
        if (stateCount[3] == 0) {
            return false;
        }

        while (centerI + i < maxI && centerJ + i < maxJ && image.get(centerJ + i, centerI + i)) {
            stateCount[4]++;
            i++;
        }

        if (stateCount[4] != 0 && stateCount[0] != 0) {
            stateCount[0] = stateCount[4] = Math.min(
                    stateCount[0], stateCount[4]
            );
        }

        if (stateCount[4] == 0) {
            return false;
        }

        return foundPatternDiagonal(stateCount);
    }

    /**
     * <p>?????????????????????????????????????????????????????????
     * ???????????????????????????????????????????????????
     * finder??????????????????????????????????????????</p>
     *
     * @param startI   ???????????????????????????????????????
     * @param centerJ  ???????????????????????????????????????????????????
     * @param maxCount ?????????????????????
     *                 ???????????????????????????????????????????????????????????????
     * @return vertical center of finder pattern??????{@link Float#NaN}????????????????????????
     */
    private float crossCheckVertical(int startI, int centerJ, int maxCount,
                                     int originalStateCountTotal) {
        BitMatrix image = this.image;

        int maxI = image.getHeight();
        int[] stateCount = getCrossCheckStateCount();

        //????????????????????????
        int i = startI;
        while (i >= 0 && image.get(centerJ, i)) {
            stateCount[2]++;
            i--;
        }
        if (i < 0) {
            return Float.NaN;
        }

        //??????????????????
        while (i >= 0 && !image.get(centerJ, i) && stateCount[1] <= maxCount) {
            stateCount[1]++;
            i--;
        }
        // If already too many modules in this state or ran off the edge:
        if (i < 0 || stateCount[1] > maxCount) {
            return Float.NaN;
        }

        //????????????????????????
        while (i >= 0 && image.get(centerJ, i)) {
            stateCount[0]++;
            i--;
        }

//        if (stateCount[0] > maxCount) {
//            return Float.NaN;
//        }

        //????????????????????????
        i = startI + 1;
        while (i < maxI && image.get(centerJ, i)) {
            stateCount[2]++;
            i++;
        }

        if (i == maxI) {
            return Float.NaN;
        }

        //??????????????????
        while (i < maxI && !image.get(centerJ, i) && stateCount[3] < maxCount) {
            stateCount[3]++;
            i++;
        }
        if (i == maxI || stateCount[3] >= maxCount) {
            return Float.NaN;
        }

        //??????????????????
        while (i < maxI && image.get(centerJ, i)) {
            stateCount[4]++;
            i++;
        }

        if (stateCount[0] != 0 && stateCount[4] != 0) {
            //???????????????
            if (stateCount[0] < stateCount[4]) {
                i -= stateCount[4] - stateCount[0];
            }
            stateCount[0] = stateCount[4] = Math.min(stateCount[0], stateCount[4]);
        }

        if (stateCount[4] >= maxCount) {
            return Float.NaN;
        }

        // If we found a finder-pattern-like section, but its size is more than 40% different than
        // the original, assume it's a false positive
        int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] +
                stateCount[4];
        if (5 * Math.abs(stateCountTotal - originalStateCountTotal) >= 2 * originalStateCountTotal) {
            return Float.NaN;
        }

        return foundPatternCross(stateCount) ? centerFromEnd(stateCount, i) : Float.NaN;
    }

    /**
     * <p>??????{@link#crossCheckVertical???int???int???int???int???}???????????????????????????
     * ???????????????????????????????????????????????????????????????
     * ???????????????????????????????????????????????????????????????</p>
     */
    private float crossCheckHorizontal(int startJ, int centerI, int maxCount,
                                       int originalStateCountTotal) {
        BitMatrix image = this.image;

        int maxJ = image.getWidth();
        int[] stateCount = getCrossCheckStateCount();

        int j = startJ;
        while (j >= 0 && image.get(j, centerI)) {
            stateCount[2]++;
            j--;
        }
        if (j < 0) {
            return Float.NaN;
        }
        //????????????????????????
        while (j >= 0 && !image.get(j, centerI) && stateCount[1] <= maxCount) {
            stateCount[1]++;
            j--;
        }
        if (j < 0 || stateCount[1] > maxCount) {
            return Float.NaN;
        }

        //??????????????????
        while (j >= 0 && image.get(j, centerI)) {
            stateCount[0]++;
            j--;
        }

        j = startJ + 1;
        while (j < maxJ && image.get(j, centerI)) {
            stateCount[2]++;
            j++;
        }
        if (j == maxJ) {
            return Float.NaN;
        }

        //????????????????????????
        while (j < maxJ && !image.get(j, centerI) && stateCount[3] < maxCount) {
            stateCount[3]++;
            j++;
        }
        if (j == maxJ || stateCount[3] >= maxCount) {
            return Float.NaN;
        }

        //??????????????????
        while (j < maxJ && image.get(j, centerI)) {
            stateCount[4]++;
            j++;
        }

        if (stateCount[0] != 0 && stateCount[4] != 0) {
            if (stateCount[0] < stateCount[4]) {
                j -= (stateCount[4] - stateCount[0]);
            }
            stateCount[0] = stateCount[4] = Math.min(stateCount[0], stateCount[4]);
        }

        if (stateCount[4] >= maxCount) {
            return Float.NaN;
        }

        // If we found a finder-pattern-like section, but its size is significantly different than
        // the original, assume it's a false positive
        int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] +
                stateCount[4];
        if (5 * Math.abs(stateCountTotal - originalStateCountTotal) >= originalStateCountTotal) {
            return Float.NaN;
        }

        return foundPatternCross(stateCount) ? centerFromEnd(stateCount, j) : Float.NaN;
    }

    /**
     * @param stateCount  ???????????????????????????????????????
     * @param i           ?????????????????????????????????
     * @param j           ????????????????????????????????????
     * @param pureBarcode ?????????
     * @return true?????????????????????finder????????????
     * @see#handlePossibleCenter???int[]???int???int???
     * @deprecated?????????????????????
     */
    @Deprecated
    protected final boolean handlePossibleCenter(int[] stateCount, int i, int j,
                                                 boolean pureBarcode) {
        return handlePossibleCenter(stateCount, i, j);
    }

    /**
     * <p>????????????????????????????????????????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????
     * ???????????????????????????????????????
     * ?????????????????????????????????????????????????????????</p>
     *
     * <p>???????????????finder??????????????????????????????????????????
     * ??????????????????????????????????????????????????????
     * ????????????????????????????????????????????????????????????????????????????????????
     * ????????????
     *
     * @param stateCount ???????????????????????????????????????
     * @param i          ?????????????????????????????????
     * @param j          ????????????????????????????????????
     * @return true?????????????????????finder????????????
     */
    protected final boolean handlePossibleCenter(int[] stateCount, int i, int j) {
        int stateCountTotal = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] +
                stateCount[4];
        float centerJ = centerFromEnd(stateCount, j);
        float centerI = crossCheckVertical(i, (int) centerJ, stateCount[2], stateCountTotal);
        if (!Float.isNaN(centerI)) {
            // Re-cross check
            centerJ = crossCheckHorizontal((int) centerJ, (int) centerI, stateCount[2], stateCountTotal);
            if (!Float.isNaN(centerJ) && crossCheckDiagonal((int) centerI, (int) centerJ)) {
                float estimatedModuleSize = stateCountTotal / 7.0f;
                boolean found = false;
                for (int index = 0; index < possibleCenters.size(); index++) {
                    FinderPattern center = possibleCenters.get(index);
                    // Look for about the same center and module size:
                    if (center.aboutEquals(estimatedModuleSize, centerI, centerJ)) {
                        possibleCenters.set(index, center.combineEstimate(centerI, centerJ, estimatedModuleSize));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    FinderPattern point = new FinderPattern(centerJ, centerI, estimatedModuleSize);
                    possibleCenters.add(point);
                    if (resultPointCallback != null) {
                        resultPointCallback.foundPossibleResultPoint(point);
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * @return??????????????????????????????????????????????????????????????? ???????????????????????????????????????????????????????????????????????????
     * ????????????????????????????????????????????????????????????
     * ???????????????
     */
    private int findRowSkip() {
        int max = possibleCenters.size();
        if (max <= 1) {
            return 0;
        }
        ResultPoint firstConfirmedCenter = null;
        for (FinderPattern center : possibleCenters) {
            if (center.getCount() >= CENTER_QUORUM) {
                if (firstConfirmedCenter == null) {
                    firstConfirmedCenter = center;
                } else {
                    // We have two confirmed centers
                    // How far down can we skip before resuming looking for the next
                    // pattern? In the worst case, only the difference between the
                    // difference in the x / y coordinates of the two centers.
                    // This is the case where you find top left last.
                    hasSkipped = true;
                    return (int) (Math.abs(firstConfirmedCenter.getX() - center.getX()) -
                            Math.abs(firstConfirmedCenter.getY() - center.getY())) / 2;
                }
            }
        }
        return 0;
    }

    /**
     * @return true iff?????????????????????3????????????????????????????????????
     * ????????????{@link#CENTER_QUORUM}????????????
     * ???????????????????????????
     */
    private boolean haveMultiplyConfirmedCenters() {
        int confirmedCount = 0;
        float totalModuleSize = 0.0f;
        int max = possibleCenters.size();
        for (FinderPattern pattern : possibleCenters) {
            if (pattern.getCount() >= CENTER_QUORUM) {
                confirmedCount++;
                totalModuleSize += pattern.getEstimatedModuleSize();
            }
        }
        if (confirmedCount < 3) {
            return false;
        }
        //????????????????????????3?????????????????????????????????????????????????????????????????????
        //?????????????????????????????????????????????
        //?????????????????????????????????????????????????????????????????????
        //????????????????????????5%???????????????
        float average = totalModuleSize / max;
        float totalDeviation = 0.0f;
        for (FinderPattern pattern : possibleCenters) {
            totalDeviation += Math.abs(pattern.getEstimatedModuleSize() - average);
        }
        return totalDeviation <= 0.05f * totalModuleSize;
    }

    /**
     * ???a???b????????????????????????
     */
    private static double squaredDistance(FinderPattern a, FinderPattern b) {
        double x = a.getX() - b.getX();
        double y = a.getY() - b.getY();
        return x * x + y * y;
    }

    /**
     * @return????????????????????????3?????????{@link FinderPattern}?????????????????????
     * ?????????????????????????????????????????????????????????????????????????????????
     * ???????????????3?????????????????????????????????@?????????NotFoundException
     */
    private FinderPattern[] selectBestPatterns() throws NotFoundException {

        int startSize = possibleCenters.size();
        if (startSize < 3) {
            // Couldn't find enough finder patterns
            throw NotFoundException.getNotFoundInstance();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            possibleCenters.sort(moduleComparator);
        }

        double distortion = Double.MAX_VALUE;
        FinderPattern[] bestPatterns = new FinderPattern[3];

        for (int i = 0; i < possibleCenters.size() - 2; i++) {
            FinderPattern fpi = possibleCenters.get(i);
            float minModuleSize = fpi.getEstimatedModuleSize();

            for (int j = i + 1; j < possibleCenters.size() - 1; j++) {
                FinderPattern fpj = possibleCenters.get(j);
                double squares0 = squaredDistance(fpi, fpj);

                for (int k = j + 1; k < possibleCenters.size(); k++) {
                    FinderPattern fpk = possibleCenters.get(k);
                    float maxModuleSize = fpk.getEstimatedModuleSize();
                    if (maxModuleSize > minModuleSize * 1.4f) {
                        // module size is not similar
                        continue;
                    }

                    double a = squares0;
                    double b = squaredDistance(fpj, fpk);
                    double c = squaredDistance(fpi, fpk);

                    // sorts ascending - inlined
                    if (a < b) {
                        if (b > c) {
                            if (a < c) {
                                double temp = b;
                                b = c;
                                c = temp;
                            } else {
                                double temp = a;
                                a = c;
                                c = b;
                                b = temp;
                            }
                        }
                    } else {
                        if (b < c) {
                            if (a < c) {
                                double temp = a;
                                a = b;
                                b = temp;
                            } else {
                                double temp = a;
                                a = b;
                                b = c;
                                c = temp;
                            }
                        } else {
                            double temp = a;
                            a = c;
                            c = temp;
                        }
                    }

                    // a^2 + b^2 = c^2 (Pythagorean theorem), and a = b (isosceles triangle).
                    // Since any right triangle satisfies the formula c^2 - b^2 - a^2 = 0,
                    // we need to check both two equal sides separately.
                    // The value of |c^2 - 2 * b^2| + |c^2 - 2 * a^2| increases as dissimilarity
                    // from isosceles right triangle.
                    double d = Math.abs(c - 2 * b) + Math.abs(c - 2 * a);
                    if (d < distortion) {
                        distortion = d;
                        bestPatterns[0] = fpi;
                        bestPatterns[1] = fpj;
                        bestPatterns[2] = fpk;
                    }
                }
            }
        }
        if (distortion == Double.MAX_VALUE) {
            throw NotFoundException.getNotFoundInstance();
        }

        return bestPatterns;
    }

    /**
     * <p>???{@link FinderPattern\getEstimatedModuleSize??????}??????</p>
     */
    private static final class EstimatedModuleComparator implements Comparator<FinderPattern>, Serializable {
        @Override
        public int compare(FinderPattern center1, FinderPattern center2) {
            return Float.compare(center1.getEstimatedModuleSize(), center2.getEstimatedModuleSize());
        }
    }

}
