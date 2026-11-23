using System;
using System.Collections.Generic;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Linq;

namespace EZShare.Core
{
    public static class NetworkInfo
    {
        public static List<string> IPv4()
        {
            var result = new List<string>();
            try
            {
                foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (nic.OperationalStatus != OperationalStatus.Up) continue;
                    if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    var props = nic.GetIPProperties();
                    foreach (var ua in props.UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily == AddressFamily.InterNetwork)
                            result.Add(ua.Address.ToString());
                    }
                }
            }
            catch { }
            if (result.Count == 0) result.Add("127.0.0.1");
            return result.Distinct().ToList();
        }

        public static string MachineName()
        {
            try { return Environment.MachineName; } catch { return "Windows"; }
        }
    }
}
