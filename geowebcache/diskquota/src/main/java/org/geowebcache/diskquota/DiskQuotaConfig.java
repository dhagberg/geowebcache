/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Gabriel Roldan (OpenGeo) 2010
 *  
 */
package org.geowebcache.diskquota;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Holds the quota configuration for all the registered layers as well as the instance wide settings
 * such as cache disk block size, maximum number of concurrent cache clean ups, etc.
 * 
 * @author groldan
 * 
 */
public class DiskQuotaConfig {

    static final int DEFAULT_DISK_BLOCK_SIZE = 4096;

    static final int DEFAULT_CLEANUP_FREQUENCY = 10;

    static final TimeUnit DEFAULT_CLEANUP_UNITS = TimeUnit.SECONDS;

    static final int DEFAULT_MAX_CONCURRENT_CLEANUPS = 2;

    static String DEFAULT_GLOBAL_POLICY_NAME = "LFU";

    private Boolean enabled;

    private int diskBlockSize;

    private int cacheCleanUpFrequency;

    private TimeUnit cacheCleanUpUnits;

    private int maxConcurrentCleanUps;

    private String globalExpirationPolicyName;

    private Quota globalQuota;

    private List<LayerQuota> layerQuotas;

    private transient Map<String, LayerQuota> layerQuotasMap;

    private transient ExpirationPolicy expirationPolicy;

    private transient boolean dirty;

    private transient Quota globalUsedQuota;

    private transient Date lastCleanUpTime;

    public DiskQuotaConfig() {
        readResolve();
    }

    /**
     * Supports initialization of instance variables during XStream deserialization
     * 
     * @return
     */
    private Object readResolve() {
        if (enabled == null) {
            enabled = Boolean.FALSE;
        }
        if (diskBlockSize == 0) {
            diskBlockSize = DEFAULT_DISK_BLOCK_SIZE;
        }
        if (cacheCleanUpFrequency == 0) {
            cacheCleanUpFrequency = DEFAULT_CLEANUP_FREQUENCY;
        }
        if (layerQuotas == null) {
            layerQuotas = new ArrayList<LayerQuota>(2);
        }
        if (maxConcurrentCleanUps == 0) {
            maxConcurrentCleanUps = DEFAULT_MAX_CONCURRENT_CLEANUPS;
        }
        if (cacheCleanUpUnits == null) {
            cacheCleanUpUnits = DEFAULT_CLEANUP_UNITS;
        }
        if (globalExpirationPolicyName == null) {
            globalExpirationPolicyName = DEFAULT_GLOBAL_POLICY_NAME;
        }
        return this;
    }

    public boolean isEnabled() {
        return enabled.booleanValue();
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public int getDiskBlockSize() {
        return diskBlockSize;
    }

    public void setDiskBlockSize(int blockSizeBytes) {
        if (blockSizeBytes <= 0) {
            throw new IllegalArgumentException("Block size shall be a positive integer");
        }
        this.diskBlockSize = blockSizeBytes;
    }

    public int getCacheCleanUpFrequency() {
        return cacheCleanUpFrequency;
    }

    public void setCacheCleanUpFrequency(int cacheCleanUpFrequency) {
        if (cacheCleanUpFrequency < 0) {
            throw new IllegalArgumentException("cacheCleanUpFrequency shall be a positive integer");
        }
        this.cacheCleanUpFrequency = cacheCleanUpFrequency;
    }

    public TimeUnit getCacheCleanUpUnits() {
        return cacheCleanUpUnits;
    }

    public void setCacheCleanUpUnits(TimeUnit cacheCleanUpUnit) {
        if (cacheCleanUpUnit == null) {
            throw new IllegalArgumentException("cacheCleanUpUnits can't be null");
        }
        this.cacheCleanUpUnits = cacheCleanUpUnit;
    }

    public List<LayerQuota> getLayerQuotas() {
        return layerQuotas;
    }

    public synchronized void setLayerQuotas(List<LayerQuota> quotas) {
        this.layerQuotas = quotas == null ? new ArrayList<LayerQuota>(2) : quotas;
        this.layerQuotasMap = null;
    }

    public LayerQuota getLayerQuota(final String layerName) {
        LayerQuota quota = getLayerQuotasMap().get(layerName);
        return quota;
    }

    private synchronized Map<String, LayerQuota> getLayerQuotasMap() {
        if (layerQuotasMap == null) {
            layerQuotasMap = new HashMap<String, LayerQuota>();

            if (layerQuotas != null) {
                for (LayerQuota lq : layerQuotas) {
                    layerQuotasMap.put(lq.getLayer(), lq);
                }
            }
        }
        return layerQuotasMap;
    }

    public synchronized void remove(final LayerQuota lq) {
        for (Iterator<LayerQuota> it = layerQuotas.iterator(); it.hasNext();) {
            LayerQuota quota = it.next();
            if (quota.getLayer().equals(lq.getLayer())) {
                it.remove();
                getLayerQuotasMap().remove(lq.getLayer());
                if (this.globalUsedQuota != null) {
                    this.globalUsedQuota.subtract(quota.getUsedQuota());
                }
                break;
            }
        }
    }

    /**
     * @return number of explicitly configured layers (ie, not affected by
     *         {@link #getDefaultQuota() default quota})
     */
    public int getNumLayers() {
        return layerQuotas.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[");
        for (LayerQuota lq : getLayerQuotas()) {
            sb.append("\n\t").append(lq);
        }
        sb.append("]");
        return sb.toString();
    }

    public int getMaxConcurrentCleanUps() {
        return maxConcurrentCleanUps;
    }

    public void setMaxConcurrentCleanUps(int nThreads) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException("nThreads shall be a positive integer: " + nThreads);
        }
        this.maxConcurrentCleanUps = nThreads;
    }

    /**
     * @return the global quota, or {@code null} if not set
     */
    public Quota getGlobalQuota() {
        return this.globalQuota;
    }

    /**
     * @param newQuota
     *            the new global quota, or {@code null} to unset
     */
    public void setGlobalQuota(final Quota newQuota) {
        if (newQuota == null) {
            this.globalQuota = null;
        } else {
            this.globalQuota = new Quota(newQuota);
        }
    }

    public ExpirationPolicy getGlobalExpirationPolicy() {
        return this.expirationPolicy;
    }

    public String getGlobalExpirationPolicyName() {
        return this.globalExpirationPolicyName;
    }

    public void setGlobalExpirationPolicy(ExpirationPolicy policy) {
        this.expirationPolicy = policy;
        if (policy == null) {
            this.globalExpirationPolicyName = null;
        } else {
            this.globalExpirationPolicyName = policy.getName();
        }
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public synchronized Quota getGlobalUsedQuota() {
        if (this.globalUsedQuota == null) {
            this.globalUsedQuota = new Quota();
            for (LayerQuota lq : getLayerQuotas()) {
                globalUsedQuota.add(lq.getUsedQuota());
            }
        }
        return globalUsedQuota;
    }

    public void setLastCleanUpTime(Date date) {
        this.lastCleanUpTime = date;
    }

    public Date getLastCleanUpTime() {
        return this.lastCleanUpTime;
    }
}
